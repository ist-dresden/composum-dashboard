package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.composum.sling.dashboard.util.Properties;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardPropertiesView.Config.class)
public class DashboardPropertiesView extends AbstractWidgetServlet implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/properties";

    @ObjectClassDefinition(name = "Composum Dashboard Properties View")
    public @interface Config {

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "properties";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 500;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "Properties";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION)
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_DESCRIPTION)
        String[] sling_servlet_extensions() default {
                "html"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_PATHS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_PATHS_DESCRIPTION)
        String[] sling_servlet_paths();
    }

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected ResourceFilter resourceFilter;

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void embedScripts(@NotNull final ResourceResolver resolver,
                             @NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest slingRequest,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            final String mode = getHtmlMode(request, HTML_MODES);
            final Resource resource = resourceFilter.getRequestResource(request);
            if (resource != null && List.of(OPTION_PAGE, OPTION_VIEW, "").contains(mode)) {
                prepareTextResponse(response, null);
                final PrintWriter writer = response.getWriter();
                writer.append("<table class=\"table table-sm table-striped\"><thead><tr>\n" +
                        "      <th scope=\"col\">Name</th>\n" +
                        "      <th scope=\"col\" width=\"100%\">Value</th>\n" +
                        "      <th scope=\"col\">Type</th>\n" +
                        "    </tr></thead><tbody>\n");
                final TreeMap<String, Object> properties = new TreeMap<>(resource.getValueMap());
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    final String name = entry.getKey();
                    if (resourceFilter.isAllowedProperty(name)) {
                        final Object value = entry.getValue();
                        writer.append("<tr><td>").append(xssapi.encodeForHTML(name)).append("</td><td>");
                        final String type = Properties.toHtml(writer, resource, name, value, resourceFilter, xssapi);
                        writer.append("</td>").append("<td>").append(type).append("</td></tr>\n");
                    }
                }
                writer.append("</tbody></table>\n");
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
}
