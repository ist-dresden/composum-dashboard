package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardPlugin;
import com.composum.sling.dashboard.service.DashboardWidget;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Proxy to reflect a view of a read only Felix Console in a dashboard widget.
 */
@Component(service = {Servlet.class, DashboardPlugin.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET,
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardFelixConsoleProxyServlet.Config.class, factory = true)
public class DashboardFelixConsoleProxyServlet extends AbstractWidgetServlet implements DashboardPlugin, ContentGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardFelixConsoleProxyServlet.class);

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/proxy";

    public static final String JQUERY_UI_SNIPPET = "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.13.2/themes/base/jquery-ui.min.css\"\n" +
            "      integrity=\"sha512-ELV+xyi8IhEApPS/pSj66+Jiw+sOT1Mqkzlh8ExXihe4zfqbWkxPRi8wptXIO9g73FSlhmquFlUOuMSoXz5IRw==\"\n" +
            "      crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\"/>\n";

    protected String webConsoleLabel;
    protected boolean allowPOST;
    protected List<String> additionalScripts;

    @Reference
    protected transient DashboardManager dashboardManager;

    /**
     * Reference for {@link #consoleServlet}.
     */
    protected transient ServiceReference<Servlet> consoleServletRef;

    /**
     * The console servlet we proxy for.
     */
    protected transient Servlet consoleServlet;

    @Override
    public void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                               @NotNull final Map<String, DashboardWidget> widgetSet) {
        // no widgets provided
    }

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) throws InvalidSyntaxException {
        super.activate(bundleContext,
                config.name(), new String[0], new String[0], config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.webConsoleLabel = config.proxied_webconsole_label();
        if (StringUtils.isBlank(this.webConsoleLabel)) {
            LOG.trace("No label for Felix Console servlet configured, not providing proxy servlet. {} {}", config.name(), config.label());
            return;
        }
        this.allowPOST = config.proxied_webconsole_POST();
        this.additionalScripts = Arrays.asList(config.proxied_webconsole_scripts());
        Collection<ServiceReference<Servlet>> candidates = bundleContext.getServiceReferences(Servlet.class,
                "(felix.webconsole.label=" + webConsoleLabel + ")");
        if (!candidates.isEmpty()) {
            this.consoleServletRef = candidates.iterator().next();
            this.consoleServlet = bundleContext.getService(consoleServletRef);
            if (candidates.size() > 1) { // very strange but seems to happen, so we try the first one.
                LOG.trace("Found {} candidates for Felix Console servlet with label '{}', expecting exactly one", candidates.size(), webConsoleLabel);
                // log the candidate properties
                for (ServiceReference<Servlet> serviceReference : candidates) {
                    StringBuilder buf = new StringBuilder();
                    for (String key : serviceReference.getPropertyKeys()) {
                        buf.append(key).append("=").append(serviceReference.getProperty(key)).append(", ");
                    }
                    LOG.error("Candidate for {}: {}", this.webConsoleLabel, buf);
                }
            }
        } else {
            LOG.trace("No candidates found for Felix Console servlet with label '{}', expecting exactly one", webConsoleLabel);
        }
    }

    @Deactivate
    protected void deactivate() {
        this.consoleServlet = null;
        ServiceReference<Servlet> ref = this.consoleServletRef;
        this.consoleServletRef = null;
        if (ref != null) {
            bundleContext.ungetService(ref);
        }
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE; // doesn't make any sense, but is required
    }

    @Override
    public void embedScripts(@NotNull final ResourceResolver resolver,
                             @NotNull final PrintWriter writer, @NotNull final String mode) {
        // embedded in htmlPageTail
        for (final String script : additionalScripts) {
            if (!script.startsWith("/") || !embedScript(resolver, script, writer)) {
                writer.append("<script src=\"").append(script).append("\"></script>\n");
            }
        }
    }

    @Override
    protected StringBuffer getAllowedRequestMethods(Map<String, Method> declaredMethods) {
        StringBuffer allowBuf = super.getAllowedRequestMethods(declaredMethods);
        if (allowPOST) {
            allowBuf.append(", ").append("POST");
        }
        return allowBuf;
    }

    @Override
    protected boolean mayService(@NotNull SlingHttpServletRequest request,
                                 @NotNull SlingHttpServletResponse response) throws ServletException,
            IOException {
        if (!super.mayService(request, response)) {
            String method = request.getMethod();
            if (allowPOST && HttpConstants.METHOD_POST.equals(method)) {
                doPost(request, response);
                return true;
            }
            return false;
        }
        return true;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request,
                      @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        doIt(request, response);
    }

    public void doPost(@NotNull final SlingHttpServletRequest request,
                       @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        doIt(request, response);
    }

    protected void doIt(@NotNull final SlingHttpServletRequest slingRequest,
                        @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final ResourceResolver resolver = slingRequest.getResourceResolver();
        if (consoleServlet == null) {
            response.setStatus(SlingHttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println("No unique Felix Console servlet with label '" + webConsoleLabel + "' found");
            return;
        }
        RequestPathInfo info = slingRequest.getRequestPathInfo();
        if ("css".equals(info.getExtension())) {
            response.setContentType("text/css");
            // include to /system/console/res/ui/webconsole.css
            //
        } else if ("js".equals(info.getExtension())) {
            response.setContentType("text/javascript");
        }
        prepareTextResponse(response, null);
        PrintWriter writer = response.getWriter();
        htmlPageHead(resolver, writer,
                JQUERY_UI_SNIPPET,
                TEMPLATE_BASE + "felixconsole/felixconsole.css",
                TEMPLATE_BASE + "felixconsole/webconsole.css",
                TEMPLATE_BASE + "felixconsole/admin_compat.css");
        consoleServlet.service(slingRequest, response);
        htmlPageTail(resolver, writer, TEMPLATE_BASE + "felixconsole/felixconsole.js");
    }

    @ObjectClassDefinition(name = "Composum Dashboard Felix Console Proxy",
            description = "provides a dashboard widget to reflect a view of a read only Felix Console view")
    public @interface Config {

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name();

        @AttributeDefinition(name = "Label of the servlet in the Felix Console",
                description = "The label of the servlet in the Felix Console that we are proxying for the dashboard - e.g. 'requests' for /system/console/requests")
        String proxied_webconsole_label();

        @AttributeDefinition(name = "allow POST", description = "allow POST requests (test requests, e.g. for resolver testing). " +
                "Use with care and only of the proxied console plugin really requires it, as this might allows unintended changes of the system state.")
        boolean proxied_webconsole_POST() default false;

        @AttributeDefinition(name = "additional scripts",
                description = "a set of additional script files to embed (script tag link) at the end of the page body")
        String[] proxied_webconsole_scripts() default {};

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 7000;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION, required = false)
        String label();

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME, required = false)
        String navTitle();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION, required = false)
        String[] sling_servlet_resourceTypes();

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_DESCRIPTION)
        String[] sling_servlet_extensions() default {
                "html"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_PATHS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_PATHS_DESCRIPTION, required = false)
        String[] sling_servlet_paths();

        @AttributeDefinition()
        String webconsole_configurationFactory_nameHint() default "'{name}' - '{proxied.webconsole.label}'";
    }
}
