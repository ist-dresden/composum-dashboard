package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardPlugin;
import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.AbstractWidgetServlet.OPTION_TILE;
import static com.composum.sling.dashboard.servlet.AbstractWidgetServlet.OPTION_VIEW;

/**
 * a primitive repository browser for a simple repository content visualization
 */
@Component(service = {Servlet.class, DashboardPlugin.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardServlet.Config.class)
public class DashboardServlet extends AbstractDashboardServlet implements DashboardPlugin, ContentGenerator {

    public static final String DASHBOARD_CONTEXT = "dashboard";

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling";

    @ObjectClassDefinition(name = "Composum Dashboard")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "dashboard";

        @AttributeDefinition(name = "Rank")
        int rank() default 9000;

        @AttributeDefinition(name = "Title")
        String title() default "Dashboard";

        @AttributeDefinition(name = "Home Url")
        String homeUrl();

        @AttributeDefinition(name = "Navigation")
        String[] navigation();

        @AttributeDefinition(name = "Resource Types",
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

    public static final String NAVIGATION_PATH = "navigation";
    public static final String WIDGETS_PATH = "widgets";

    public static final String PAGE_TEMPLATES = "/com/composum/sling/dashboard/page/";

    public static final Pattern NAVIGATION_PATTERN = Pattern.compile("^(?<name>[^:]+):(?<label>[^:]*):(?<link>.+)$");

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected DashboardManager dashboardManager;

    protected int rank;
    protected String title;
    protected String homeUrl;
    protected List<String> navigation;

    protected final Map<String, DashboardWidget> dashboardWidgets = new HashMap<>();

    @Reference(
            service = DashboardWidget.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY
    )
    protected void addDashboardWidget(@NotNull final DashboardWidget widget) {
        if (widget.getContext().contains(DASHBOARD_CONTEXT)) {
            synchronized (dashboardWidgets) {
                dashboardWidgets.put(widget.getName(), widget);
            }
        }
    }

    protected void removeDashboardWidget(@NotNull final DashboardWidget widget) {
        synchronized (dashboardWidgets) {
            dashboardWidgets.remove(widget.getName());
        }
    }

    @Override
    public void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                               @NotNull final Map<String, DashboardWidget> widgetSet) {
        for (DashboardWidget widget : dashboardWidgets.values()) {
            if (context == null || widget.getContext().contains(context)) {
                if (!widgetSet.containsKey(widget.getName())) {
                    widgetSet.put(widget.getName(), widget);
                }
            }
        }
    }

    @Activate
    @Modified
    protected void activate(BundleContext bundleContext, Config config) {
        super.activate(bundleContext, config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.rank = config.rank();
        this.title = config.title();
        this.homeUrl = config.homeUrl();
        this.navigation = Arrays.asList(config.navigation());
    }

    @Override
    public int getRank() {
        return rank;
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
        if (!createContent(request, response, dashboardManager, this)) {
            final DashboardWidget currentWidget = getCurrentWidget(request);
            prepareTextResponse(response, null);
            final PrintWriter writer = response.getWriter();
            final ResourceResolver resolver = request.getResourceResolver();
            final Map<String, Object> properties = new HashMap<>();
            properties.put("html-css-classes", getHtmlCssClasses("composum-dashboard__page"));
            properties.put("title", xssapi.encodeForHTML(getTitle(request)));
            properties.put("home-url", xssapi.encodeForHTMLAttr(StringUtils.defaultString(homeUrl, getPagePath(request) + ".html")));
            properties.put("dashboardPath", getPagePath(request));
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
    }

    public void includeWidget(@NotNull final SlingHttpServletRequest request,
                              @NotNull final SlingHttpServletResponse response,
                              @NotNull final DashboardWidget widget, @NotNull final String selector)
            throws ServletException, IOException {
        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        options.setReplaceSelectors(selector);
        Resource widgetResource = request.getResource().getChild(WIDGETS_PATH + "/" + widget.getName());
        if (widgetResource == null) {
            widgetResource = widget.getWidgetResource(request);
        }
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
                writer.append("<div class=\"composum-dashboard__widget col-lg-4 col-md-6 col-12\"><a href=\"#\" data-href=\"")
                        .append(getPagePath(request)).append(".html/").append(widget.getName())
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
                    writer.append("<li class=\"nav-item\"><a class=\"nav-link\" href=\"#\" data-href=\"")
                            .append(linkUrl).append("\"");
                    if (StringUtils.isNotBlank(title)) {
                        writer.append(" title =\"").append(title).append("\"");
                    }
                    writer.append(">").append(values.get("label", values.get("jcr:title",
                            item.getName()))).append("</a></li>");
                }
            }
        } else {
            for (final String item : this.navigation) {
                final Matcher matcher = NAVIGATION_PATTERN.matcher(item);
                final String linkUrl = matcher.group("link");
                if (StringUtils.isNotBlank(linkUrl)) {
                    final String name = matcher.group("name");
                    if (matcher.matches()) {
                        final String label = StringUtils.defaultString(matcher.group("label"), name);
                        writer.append("<li class=\"nav-item\"><a class=\"nav-link\" href=\"#\" data-href=\"")
                                .append(linkUrl).append("\"");
                        if (StringUtils.isNotBlank(label)) {
                            writer.append(" title =\"").append(label).append("\"");
                        }
                        writer.append(">").append(label).append("</a></li>");
                    }
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
                && (StringUtils.isBlank(linkUrl) || linkUrl.matches("^(https?)?//.*$")
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

    public @Nullable Resource createContent(@NotNull final SlingHttpServletRequest request,
                                            @NotNull final Resource parent,
                                            @NotNull final String name, @NotNull final String primaryType)
            throws PersistenceException {
        Resource resource = createContent(parent, name, primaryType, title, resourceType);
        if (resource != null) {
            if (!this.navigation.isEmpty()) {
                final Resource navigation = createContent(resource, NAVIGATION_PATH, NT_UNSTRUCTURED, null, null);
                if (navigation != null) {
                    for (final String item : this.navigation) {
                        final Matcher matcher = NAVIGATION_PATTERN.matcher(item);
                        if (matcher.matches()) {
                            final String itemLink = matcher.group("link");
                            if (StringUtils.isNotBlank(itemLink)) {
                                final String itemName = matcher.group("name");
                                createContent(navigation, itemName, NT_UNSTRUCTURED,
                                        StringUtils.defaultString(matcher.group("label"), itemName), null,
                                        itemLink.startsWith("/") && !itemLink.startsWith("//") && !itemLink.contains(".")
                                                ? "linkPath" : "linkUrl", itemLink);
                            }
                        }
                    }
                }
            }
            final Resource widgets = createContent(resource, WIDGETS_PATH, NT_UNSTRUCTURED, null, null);
            if (widgets != null) {
                for (final DashboardWidget widget : getWidgets(request)) {
                    if (widget instanceof ContentGenerator) {
                        ((ContentGenerator) widget).createContent(request, widgets, widget.getName(), NT_UNSTRUCTURED);
                    }
                }
            }
        }
        return resource;
    }
}
