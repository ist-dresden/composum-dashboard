package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardPlugin;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.apache.sling.xss.XSSFilter;
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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.composum.sling.dashboard.DashboardConfig.JCR_CONTENT;
import static com.composum.sling.dashboard.DashboardConfig.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.NT_UNSTRUCTURED;

/**
 * a primitive repository browser for a simple repository content visualization
 */
@Component(service = {Servlet.class, DashboardPlugin.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardBrowserServlet.Config.class)
public class DashboardBrowserServlet extends AbstractWidgetServlet implements DashboardPlugin, ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/browser";

    @ObjectClassDefinition(name = "Composum Dashboard Browser")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "browser";

        @AttributeDefinition(name = "Category")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category();

        @AttributeDefinition(name = "Rank")
        int rank() default 6000;

        @AttributeDefinition(name = "Label")
        String label() default "Browser";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle() default "Browser";

        @AttributeDefinition(name = "Home Url")
        String homeUrl() default "https://www.composum.com";

        @AttributeDefinition(name = "Toolbar")
        String[] toolbar() default {
                "Open:television:_blank:${path}[.html]"
        };

        @AttributeDefinition(name = "Resource Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/form",
                DEFAULT_RESOURCE_TYPE + "/tool",
                DEFAULT_RESOURCE_TYPE + "/tree"
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "html",
                "json"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servlet paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    public static final String BROWSER_CONTEXT = "browser";

    public static final String TEMPLATE_BASE = "/com/composum/sling/dashboard/browser/";
    public static final String PAGE_TEMPLATE = TEMPLATE_BASE + "page.html";
    public static final String PAGE_TAIL = TEMPLATE_BASE + "tail.html";
    protected static final String OPTION_TREE = "tree";
    protected static final String OPTION_TOOL = "tool";
    protected static final List<String> HTML_MODES =
            Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_FORM, OPTION_TOOL, OPTION_TREE);

    @Reference
    protected XSSAPI xssapi;
    @Reference
    protected XSSFilter xssFilter;

    @Reference
    protected DashboardManager dashboardManager;

    protected String homeUrl;
    protected List<String> toolbar;

    protected final Map<String, DashboardWidget> viewWidgets = new HashMap<>();
    protected final Map<String, DashboardWidget> toolWidgets = new HashMap<>();

    @Reference(
            service = DashboardWidget.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY
    )
    protected void addDashboardWidget(@NotNull final DashboardWidget widget) {
        if (widget.getContext().contains(BROWSER_CONTEXT)) {
            if (widget.getCategory().contains("tool")) {
                synchronized (toolWidgets) {
                    toolWidgets.put(widget.getName(), widget);
                }
            } else {
                synchronized (viewWidgets) {
                    viewWidgets.put(widget.getName(), widget);
                }
            }
        }
    }

    protected void removeDashboardWidget(@NotNull final DashboardWidget widget) {
        synchronized (viewWidgets) {
            viewWidgets.remove(widget.getName());
        }
        synchronized (toolWidgets) {
            toolWidgets.remove(widget.getName());
        }
    }

    @Override
    public void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                               @NotNull final Map<String, DashboardWidget> widgetSet) {
        provideWidgets(request, context, widgetSet, viewWidgets.values());
        provideWidgets(request, context, widgetSet, toolWidgets.values());
    }

    protected void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                                  @NotNull final Map<String, DashboardWidget> widgetSet,
                                  @NotNull Collection<DashboardWidget> browserSet) {
        for (DashboardWidget widget : browserSet) {
            if (context == null || widget.getContext().contains(context)) {
                if (BROWSER_CONTEXT.equals(context) || !widgetSet.containsKey(widget.getName())) {
                    widgetSet.put(widget.getName(), widget);
                }
            }
        }
    }

    protected Iterable<DashboardWidget> getWidgets(Map<String, DashboardWidget> browserSet) {
        final List<DashboardWidget> widgets = new ArrayList<>(browserSet.values());
        widgets.sort(DashboardWidget.COMPARATOR);
        return widgets;
    }

    public @Nullable Resource createContent(@NotNull final SlingHttpServletRequest request,
                                            @NotNull final Resource parent,
                                            @NotNull final String name, @NotNull final String primaryType)
            throws PersistenceException {
        Resource browser = super.createContent(request, parent, name, primaryType);
        if (browser != null) {
            createContent(request, browser, OPTION_VIEW, viewWidgets.values());
            createContent(request, browser, OPTION_TOOL, toolWidgets.values());
        }
        return browser;
    }

    protected void createContent(@NotNull final SlingHttpServletRequest request, @NotNull final Resource browser,
                                 @NotNull final String section, Iterable<DashboardWidget> widgetSet)
            throws PersistenceException {
        final Resource option = browser.getChild(section);
        if (option != null) {
            for (final DashboardWidget widget : widgetSet) {
                if (widget instanceof ContentGenerator) {
                    ((ContentGenerator) widget).createContent(request, option, widget.getName(), NT_UNSTRUCTURED);
                }
            }
        }
    }

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.homeUrl = config.homeUrl();
        this.toolbar = Arrays.asList(config.toolbar());
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest slingRequest,
                      @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            final RequestPathInfo pathInfo = request.getRequestPathInfo();
            if ("html".equals(pathInfo.getExtension())) {
                final String mode = getHtmlMode(request, HTML_MODES);
                switch (mode) {
                    case OPTION_TREE:
                        jsonTree(request, response);
                        break;
                    case OPTION_VIEW:
                        htmlView(request, response);
                        break;
                    case OPTION_FORM:
                        htmlForm(request, response);
                        break;
                    case OPTION_TOOL:
                        htmlTool(request, response);
                        break;
                    case OPTION_PAGE:
                    default:
                        if (!createContent(request, response, dashboardManager, this)) {
                            browserPage(request, response);
                        }
                        break;
                }
            } else {
                jsonTree(request, response);
            }
        }
    }

    protected boolean isFavoritesSupported() {
        for (DashboardWidget widget : toolWidgets.values()) {
            if (widget.getCategory().contains("favorites")) {
                return true;
            }
        }
        return false;
    }

    protected void collectHtmlCssClasses(@NotNull final Set<String> cssClasses) {
        if (isFavoritesSupported()) {
            cssClasses.add("browser-favorites");
        }
    }

    protected void browserPage(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final ResourceResolver resolver = request.getResourceResolver();
        final Resource browser = getWidgetResource(request, resourceType);
        if (browser != null) {
            final String targetPath = Optional.ofNullable(request.getRequestPathInfo().getSuffix()).orElse("");
            final ValueMap values = browser.getValueMap();
            final Map<String, Object> properties = new HashMap<>();
            properties.put("html-css-classes", getHtmlCssClasses("dashboard-browser__page"));
            properties.put("home-url", xssapi.encodeForHTMLAttr(homeUrl));
            properties.put("browser-label", xssapi.encodeForHTML(getLabel()));
            properties.put("status-line", xssapi.encodeForHTML(targetPath));
            properties.put("browser-toolbar-elements", toolbar(request));
            properties.put("browser-tool-nav-elements", toolNavigation(request));
            properties.put("target-path", xssapi.encodeForHTMLAttr(targetPath));
            properties.put("browser-path", xssapi.encodeForHTMLAttr(browser.getPath()));
            properties.put("browser-uri", xssapi.encodeForHTMLAttr(getWidgetUri(request, resourceType, HTML_MODES)));
            properties.put("browser-tree", xssapi.encodeForHTMLAttr(getWidgetUri(request, resourceType, HTML_MODES, OPTION_TREE)));
            properties.put("browser-view", xssapi.encodeForHTMLAttr(getWidgetUri(request, resourceType, HTML_MODES, OPTION_VIEW)));
            properties.put("browser-tab-view", xssapi.encodeForHTMLAttr(getWidgetUri(request, resourceType, HTML_MODES, OPTION_VIEW, "#id#")));
            properties.put("browser-tab-form", xssapi.encodeForHTMLAttr(getWidgetUri(request, resourceType, HTML_MODES, OPTION_VIEW, "#id#", OPTION_FORM)));
            properties.put("loginUrl", xssapi.encodeForHTMLAttr(dashboardManager.getLoginUri()));
            properties.put("currentUser", xssapi.encodeForHTML(resolver.getUserID()));
            prepareTextResponse(response, null);
            PrintWriter writer = response.getWriter();
            copyResource(getClass(), PAGE_TEMPLATE, writer, properties);
            for (DashboardWidget widget : viewWidgets.values()) {
                widget.embedScript(writer, OPTION_VIEW);
            }
            for (DashboardWidget widget : toolWidgets.values()) {
                widget.embedScript(writer, OPTION_VIEW);
            }
            copyResource(getClass(), PAGE_TAIL, writer, properties);
        }
    }

    protected @NotNull String toolbar(@NotNull final SlingHttpServletRequest request) {
        final StringWriter writer = new StringWriter();
        for (final String item : toolbar) {
            final String[] parts = StringUtils.split(item, ":", 4);
            if (parts.length == 4) {
                writer.append("<li class=\"nav-item\"><a class=\"path-link nav-link\" href=\"#\" data-path-uri=\"")
                        .append(xssapi.encodeForHTMLAttr(request.getContextPath() + parts[3]))
                        .append("\" title=\"").append(xssapi.encodeForHTMLAttr(parts[0]))
                        .append("\" data-target=\"").append(xssapi.encodeForHTMLAttr(parts[2]))
                        .append("\"><i class=\"nav-icon fa fa-").append(xssapi.encodeForHTMLAttr(parts[1]))
                        .append("\"></i></a></li>\n");
            }
        }
        return writer.toString();
    }

    protected @NotNull String toolNavigation(@NotNull final SlingHttpServletRequest request) {
        final StringWriter writer = new StringWriter();
        for (final DashboardWidget tool : getWidgets(toolWidgets)) {
            final String toolUri = getWidgetUri(request, resourceType, HTML_MODES, OPTION_TOOL, tool.getName());
            final String toolIcon = tool.getProperty("icon", "ellipsis-h");
            writer.append("<li class=\"nav-item browser-tool-").append(tool.getName())
                    .append("\"><a class=\"tool-link nav-link\" href=\"#\" data-tool-uri=\"")
                    .append(xssapi.encodeForHTMLAttr(toolUri))
                    .append("\" title=\"").append(xssapi.encodeForHTMLAttr(tool.getLabel()))
                    .append("\"><i class=\"nav-icon fa fa-").append(xssapi.encodeForHTMLAttr(toolIcon))
                    .append("\"></i></a></li>\n");
        }
        return writer.toString();
    }

    protected void htmlTool(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        htmlForward(request, response, toolWidgets, OPTION_VIEW);
    }

    protected void htmlForm(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        htmlForward(request, response, viewWidgets, OPTION_FORM);
    }

    protected void htmlForward(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response,
                               @NotNull final Map<String, DashboardWidget> widgets,
                               @NotNull final String... selectors)
            throws ServletException, IOException {
        final String submode = getHtmlSubmode(request, Collections.emptyList());
        if (StringUtils.isNotBlank(submode)) {
            final DashboardWidget selectedView = StringUtils.isNotBlank(submode) ? widgets.get(submode) : null;
            if (selectedView != null) {
                htmlView(request, response, selectedView, selectors);
            }
        }
    }

    public void htmlView(@NotNull final SlingHttpServletRequest request,
                         @NotNull final SlingHttpServletResponse response,
                         @NotNull final DashboardWidget view, @Nullable final String... selectors)
            throws ServletException, IOException {
        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        if (selectors != null && selectors.length > 0) {
            options.setReplaceSelectors(StringUtils.join(selectors, "."));
        }
        final Resource widgetResource = view.getWidgetResource(request);
        final RequestDispatcher dispatcher = request.getRequestDispatcher(widgetResource, options);
        if (dispatcher != null) {
            dispatcher.include(request, response);
        }
    }

    protected boolean htmlView(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response,
                               @NotNull final Map<String, DashboardWidget> widgets)
            throws ServletException, IOException {
        final Resource resource = dashboardManager.getRequestResource(request);
        if (resource != null) {
            prepareTextResponse(response, null);
            final String submode = getHtmlSubmode(request, Collections.emptyList());
            if (StringUtils.isNotBlank(submode)) {
                final DashboardWidget selectedView = StringUtils.isNotBlank(submode) ? widgets.get(submode) : null;
                if (selectedView != null) {
                    htmlView(request, response, selectedView, OPTION_VIEW);
                }
                return true;
            }
        }
        return false;
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final Resource resource = dashboardManager.getRequestResource(request);
        if (resource != null) {
            if (!htmlView(request, response, viewWidgets)) {
                final Iterable<DashboardWidget> widgets = getWidgets(viewWidgets);
                final PrintWriter writer = response.getWriter();
                if (isFavoritesSupported()) {
                    writer.append("<div class=\"dashboard-browser__favorite-toggle fa fa-star-o\"></div>\n");
                }
                writer.append("<ul class=\"dashboard-browser__tabs nav nav-tabs\" role=\"tablist\">\n");
                for (final DashboardWidget view : widgets) {
                    final String viewId = view.getName();
                    writer.append("<li class=\"nav-item\"><a class=\"nav-link\" id=\"").append(xssapi.encodeForHTMLAttr(viewId))
                            .append("-tab\" data-toggle=\"tab\" href=\"#").append(xssapi.encodeForHTMLAttr(viewId))
                            .append("\" role=\"tab\" aria-controls=\"").append(xssapi.encodeForHTMLAttr(viewId))
                            .append("\" aria-selected=\"false\">").append(xssapi.encodeForHTML(view.getLabel()))
                            .append("</a></li>\n");
                }
                writer.append("</ul></div>\n");
                writer.append("<div class=\"dashboard-browser__parameters\"><form class=\"form-inline\">\n");
                writer.append("</form></div>\n");
                writer.append("<div class=\"dashboard-browser__action-reload fa fa-refresh\"></div>\n");
                writer.append("<div class=\"dashboard-browser__tabs-content tab-content\">\n");
                for (final DashboardWidget view : widgets) {
                    final String viewId = view.getName();
                    writer.append("<div class=\"tab-pane\" id=\"").append(xssapi.encodeForHTMLAttr(viewId))
                            .append("\" role=\"tabpanel\" aria-labelledby=\"").append(xssapi.encodeForHTMLAttr(viewId))
                            .append("-tab\">\n");
                    //htmlView(request, response, view);
                    writer.append("</div>\n");
                }
                writer.append("</div>\n");
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    protected void jsonTree(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response)
            throws IOException {
        Resource resource = null;
        if (StringUtils.isBlank(request.getRequestPathInfo().getSuffix())) {
            final String url = xssFilter.filter(request.getParameter("url"));
            // if a 'url' parameter is used a given URL should be mapped to a resource
            // instead of using the resources path given by the requests suffix
            if (StringUtils.isNotBlank(url)) {
                resource = resolveUrl(request, url);
                if (resource == null || !dashboardManager.isAllowedResource(resource)) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        }
        if (resource == null) {
            resource = dashboardManager.getRequestResource(request);
        }
        if (resource != null) {
            response.setContentType("application/json;charset=UTF-8");
            final JsonWriter writer = new JsonWriter(response.getWriter());
            writeJsonNode(writer, resource);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public void writeJsonNode(@NotNull final JsonWriter writer, @NotNull final Resource resource)
            throws IOException {
        final String primaryType = resource.getValueMap().get(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
        writer.beginObject();
        writeNodeIdentifiers(writer, resource);
        writer.name("children").beginArray();
        final Map<String, Resource> children = dashboardManager.isSortableType(primaryType)
                ? new TreeMap<>() : new LinkedHashMap<>();
        getChildren(resource, children);
        for (Resource child : children.values()) {
            writer.beginObject();
            writeNodeIdentifiers(writer, child);
            writer.name("state").beginObject();
            writer.name("loaded").value(false);
            writer.endObject();
            writer.endObject();
        }
        writer.endArray();
        writer.endObject();
    }

    protected void getChildren(@NotNull final Resource resource, @NotNull final Map<String, Resource> children) {
        for (Resource child : resource.getChildren()) {
            if (dashboardManager.isAllowedResource(child)) {
                children.put(child.getName(), child);
            }
        }
    }

    public void writeNodeIdentifiers(@NotNull final JsonWriter writer, @NotNull final Resource resource)
            throws IOException {
        final ValueMap values = resource.getValueMap();
        final String path = resource.getPath();
        String name = resource.getName();
        if (StringUtils.isBlank(name) && "/".equals(path)) {
            name = "jcr:root";
        }
        writer.name("id").value(path);
        writer.name("name").value(resource.getName());
        writer.name("text").value(name);
        writer.name("path").value(path);
        String resourceType = resource.getResourceType();
        if (StringUtils.isNotBlank(resourceType)) {
            writer.name("resourceType").value(resourceType);
        }
        String type = getTypeKey(resource);
        if (StringUtils.isNotBlank(type)) {
            writer.name("type").value(type);
        } else if (ResourceUtil.isSyntheticResource(resource)) {
            writer.name("type").value("synthetic");
        }
    }

    public String getTypeKey(Resource resource) {
        String type = getPrimaryTypeKey(resource);
        if ("file".equals(type)) {
            type = getFileTypeKey(resource, "file-");
        } else if ("resource".equals(type)) {
            type = getMimeTypeKey(resource, "resource-");
        } else if (StringUtils.isBlank(type) || "unstructured".equals(type)) {
            type = getResourceTypeKey(resource, "resource-");
        }
        return type;
    }

    public String getPrimaryTypeKey(Resource resource) {
        String type = resource.getValueMap().get(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
        if (StringUtils.isNotBlank(type)) {
            int namespace = type.lastIndexOf(':');
            if (namespace >= 0) {
                type = type.substring(namespace + 1);
            }
            type = type.toLowerCase();
        }
        return type;
    }

    public static String getResourceTypeKey(Resource resource, String prefix) {
        String primaryType = resource.getValueMap().get(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
        String type = null;
        String resourceType = resource.getResourceType();
        if (StringUtils.isNotBlank(resourceType) && !resourceType.equals(primaryType)) {
            int namespace = resourceType.lastIndexOf(':');
            if (namespace >= 0) {
                resourceType = resourceType.substring(namespace + 1);
            }
            int dot = resourceType.lastIndexOf('.');
            if (dot >= 0) {
                resourceType = resourceType.substring(dot + 1);
            }
            type = resourceType.substring(resourceType.lastIndexOf('/') + 1);
            type = type.toLowerCase();
        }
        if (StringUtils.isNotBlank(type) && StringUtils.isNotBlank(prefix)) {
            type = prefix + type;
        }
        return type;
    }

    public static String getFileTypeKey(Resource resource, String prefix) {
        String type = null;
        Resource content = resource.getChild(JCR_CONTENT);
        if (content != null) {
            type = getMimeTypeKey(content, prefix);
        }
        return type;
    }

    public static String getMimeTypeKey(Resource resource, String prefix) {
        String type = null;
        String mimeType = resource.getValueMap().get("jcr:mimeType", String.class);
        if (StringUtils.isNotBlank(mimeType)) {
            type = getMimeTypeKey(mimeType);
        }
        if (StringUtils.isNotBlank(type) && StringUtils.isNotBlank(prefix)) {
            type = prefix + type;
        }
        return type;
    }

    @NotNull
    public static String getMimeTypeKey(String mimeType) {
        int delim = mimeType.indexOf('/');
        String major = mimeType.substring(0, delim);
        String minor = mimeType.substring(delim + 1);
        String type = major;
        if ("text".equals(major)) {
            type += "-" + minor;
        } else if ("application".equals(major)) {
            type = minor;
        }
        return type.toLowerCase();
    }
}
