package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.composum.sling.dashboard.servlet.AbstractWidgetServlet.OPTION_TILE;
import static com.composum.sling.dashboard.servlet.AbstractWidgetServlet.OPTION_VIEW;

/**
 * a primitive repository browser for a simple repository content visualization
 */
@Component(service = {Servlet.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardServlet.Config.class)
public class DashboardServlet extends AbstractDashboardServlet {

    public static final String DASHBOARD_CONTEXT = "dashboard";

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling";

    @ObjectClassDefinition(name = "Composum Dashboard")
    public @interface Config {

        @AttributeDefinition(name = "Title")
        String title() default "Dashboard";

        @AttributeDefinition(name = "Servlet Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "html"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servletd paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    public static final String PAGE_TEMPLATES = "/com/composum/sling/dashboard/page/";

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected DashboardManager dashboardManager;

    protected String title;

    @Activate
    @Modified
    protected void activate(Config config) {
        super.activate(config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.title = config.title();
    }

    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    public @Nullable DashboardWidget getCurrentWidget(@NotNull final SlingHttpServletRequest request) {
        String suffix = StringUtils.defaultString(request.getRequestPathInfo().getSuffix(), "");
        while (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        return StringUtils.isNotBlank(suffix) ? getWidget(request, suffix) : null;
    }

    public @NotNull String getTitle(@NotNull final SlingHttpServletRequest request) {
        final Resource resource = request.getResource();
        final DashboardWidget currentWidget = getCurrentWidget(request);
        return currentWidget != null ? currentWidget.getLabel() : title;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request,
                      @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final DashboardWidget currentWidget = getCurrentWidget(request);
        response.setContentType("text/html;charset=UTF-8");
        final PrintWriter writer = response.getWriter();
        final ResourceResolver resolver = request.getResourceResolver();
        final Map<String, Object> properties = new HashMap<>();
        properties.put("title", xssapi.encodeForHTML(getTitle(request)));
        properties.put("dashboardPath", getDashboardPath(request));
        copyResource(getClass(), PAGE_TEMPLATES + "head.html", writer, properties);
        htmlNavigation(request, response, writer);
        if (currentWidget != null) {
            copyResource(getClass(), PAGE_TEMPLATES + "close.html", writer, properties);
        }
        writer.append("</nav>\n");
        htmlDashboard(request, response, writer);
        copyResource(getClass(), PAGE_TEMPLATES + "script.html", writer, properties);
        if (currentWidget != null) {
            currentWidget.embedScript(writer, OPTION_VIEW);
        } else {
            for (final DashboardWidget widget : getWidgets(request)) {
                widget.embedScript(writer, OPTION_TILE);
            }
        }
        copyResource(getClass(), PAGE_TEMPLATES + "tail.html", writer, properties);
    }

    public void includeWidget(@NotNull final SlingHttpServletRequest request,
                              @NotNull final SlingHttpServletResponse response,
                              @NotNull final DashboardWidget widget, @NotNull final String selector)
            throws ServletException, IOException {
        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        options.setReplaceSelectors(selector);
        final Resource widgetResource = widget.getWidgetResource(request);
        final RequestDispatcher dispatcher = request.getRequestDispatcher(widgetResource, options);
        if (dispatcher != null) {
            dispatcher.include(request, response);
        }
    }

    protected void htmlDashboard(@NotNull final SlingHttpServletRequest request,
                                 @NotNull final SlingHttpServletResponse response,
                                 @NotNull final PrintWriter writer)
            throws ServletException, IOException {
        final DashboardWidget currentWidget = getCurrentWidget(request);
        if (currentWidget != null) {
            writer.append("<div class=\"composum-dashboard__widget-view\">");
            includeWidget(request, response, currentWidget, "view");
            writer.append("</div>");
        } else {
            writer.append("<div class=\"composum-dashboard__content container-fluid mt-3 mb-3\">\n");
            writer.append("<div class=\"composum-dashboard__widgets row\">\n");
            for (final DashboardWidget widget : getWidgets(request)) {
                writer.append("<div class=\"composum-dashboard__widget col-lg-4 col-md-6 col-12\"><a href=\"")
                        .append(getDashboardPath(request)).append(".html/").append(widget.getName())
                        .append("\" style=\"text-decoration: none;\">\n");
                includeWidget(request, response, widget, "tile");
                writer.append("</a></div>\n");
            }
            writer.append("</div></div>");
        }
    }

    protected void htmlNavigation(@NotNull final SlingHttpServletRequest request,
                                  @NotNull final SlingHttpServletResponse response,
                                  @NotNull final PrintWriter writer) {
        writer.append("<ul class=\"navbar-nav mr-auto\">");
        final Resource dashboard = request.getResource();
        final ResourceResolver resolver = dashboard.getResourceResolver();
        final Resource navigation = Optional.ofNullable(dashboard.getChild("navigation"))
                .orElse(dashboard.getChild("jcr:content/navigation"));
        if (navigation != null) {
            for (final Resource item : navigation.getChildren()) {
                final ValueMap values = item.getValueMap();
                final String linkUrl = linkUrl(resolver, values);
                if (StringUtils.isNotBlank(linkUrl)) {
                    final String title = values.get("title", values.get("jcr:title", ""));
                    writer.append("<li class=\"nav-item\"><a class=\"nav-link\" href=\"")
                            .append(linkUrl).append("\"");
                    if (StringUtils.isNotBlank(title)) {
                        writer.append(" title =\"").append(title).append("\"");
                    }
                    writer.append(">").append(values.get("label", values.get("jcr:title",
                            item.getName()))).append("</a></li>");
                }
            }
        }
        writer.append("</ul>");
    }

    protected @Nullable String linkUrl(@NotNull final ResourceResolver resolver, @NotNull final ValueMap values) {
        final String linkUrl = values.get("linkUrl", String.class);
        final String linkPath = values.get("linkPath", String.class);
        return (StringUtils.isNotBlank(linkUrl) || StringUtils.isNotBlank(linkPath))
                && (StringUtils.isBlank(linkPath) || resolver.getResource(linkPath) != null)
                && (StringUtils.isBlank(linkUrl) || linkUrl.matches("^https?//")
                || !ResourceUtil.isNonExistingResource(resolver.resolve(linkUrl)))
                ? StringUtils.isNotBlank(linkUrl) ? linkUrl : (linkPath + ".html") : null;
    }

    protected @Nullable DashboardWidget getWidget(@NotNull final SlingHttpServletRequest request,
                                                  @Nullable String name) {
        if (StringUtils.isNotBlank(name)) {
            while (name.startsWith("/")) {
                name = name.substring(1);
            }
        }
        if (StringUtils.isNotBlank(name)) {
            return dashboardManager.getWidget(request, DASHBOARD_CONTEXT, name);
        }
        return null;
    }

    public Collection<DashboardWidget> getWidgets(@NotNull final SlingHttpServletRequest request) {
        return dashboardManager.getWidgets(request, DASHBOARD_CONTEXT);
    }

    public @NotNull String getDashboardPath(@NotNull final SlingHttpServletRequest request) {
        return StringUtils.substringBefore(request.getResource().getPath(), "/jcr:content");
    }
}
