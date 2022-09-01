package com.composum.sling.dashboard.servlet.impl;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardPlugin;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.servlet.AbstractWidgetServlet;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestPathInfo;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * a primitive repository browser for a simple repository content visualization
 */
@Component(service = {Servlet.class, DashboardPlugin.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardBrowserServlet.Config.class)
public class DashboardBrowserServlet extends AbstractWidgetServlet implements DashboardPlugin {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/components/browser";

    @ObjectClassDefinition(name = "Composum Dashboard Browser")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "browser";

        @AttributeDefinition(name = "Category")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String category();

        @AttributeDefinition(name = "Rank")
        int rank() default 2000;

        @AttributeDefinition(name = "Label")
        String label() default "Browser";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Servlet Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/tree"
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "html",
                "json"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servletd paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    public static final String BROWSER_CONTEXT = "browser";

    public static final String PAGE_TEMPLATE = "/com/composum/sling/dashboard/plugin/browser/page.html";
    protected static final String OPTION_TREE = "tree";
    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_TREE);

    public static final String JCR_CONTENT = "jcr:content";
    public static final String JCR_DATA = "jcr:data";
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String JCR_MIXIN_TYPES = "jcr:mixinTypes";
    public static final String NT_UNSTRUCTURED = "nt:unstructured";
    public static final String NT_RESOURCE = "nt:resource";
    public static final String NT_FILE = "nt:file";

    @Reference
    protected XSSAPI xssapi;
    @Reference
    protected XSSFilter xssFilter;

    @Reference
    protected DashboardManager dashboardManager;

    protected final Map<String, DashboardWidget> viewWidgets = new HashMap<>();

    @Reference(
            service = DashboardWidget.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE
    )
    protected void addDashboardWidget(@NotNull final DashboardWidget widget) {
        if (widget.getContext().contains(BROWSER_CONTEXT)) {
            synchronized (viewWidgets) {
                viewWidgets.put(widget.getName(), widget);
            }
        }
    }

    protected void removeDashboardWidget(@NotNull final DashboardWidget widget) {
        synchronized (viewWidgets) {
            viewWidgets.remove(widget.getName());
        }
    }

    @Override
    public void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                               @NotNull final Map<String, DashboardWidget> widgetSet) {
        synchronized (viewWidgets) {
            for (DashboardWidget widget : viewWidgets.values()) {
                if (context == null || widget.getContext().contains(context)) {
                    if (BROWSER_CONTEXT.equals(context) || !widgetSet.containsKey(widget.getName())) {
                        widgetSet.put(widget.getName(), widget);
                    }
                }
            }
        }
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        super.activate(config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
    }

    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request,
                      @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        if ("html".equals(pathInfo.getExtension())) {
            switch (getHtmlMode(request, HTML_MODES)) {
                case OPTION_TREE:
                    jsonTree(request, response);
                    break;
                case OPTION_VIEW:
                    htmlView(request, response);
                    break;
                case OPTION_PAGE:
                default:
                    browserPage(request, response);
                    break;
            }
        } else {
            jsonTree(request, response);
        }
    }

    protected void browserPage(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final ResourceResolver resolver = request.getResourceResolver();
        final Resource browser = getWidgetResource(request, DEFAULT_RESOURCE_TYPE);
        if (browser != null) {
            final String targetPath = Optional.ofNullable(request.getRequestPathInfo().getSuffix()).orElse("");
            final ValueMap values = browser.getValueMap();
            final Map<String, Object> properties = new HashMap<>();
            properties.put("status-line", xssapi.encodeForHTML(targetPath));
            properties.put("target-path", xssapi.encodeForHTMLAttr(targetPath));
            properties.put("browser-path", xssapi.encodeForHTMLAttr(browser.getPath()));
            properties.put("browser-uri", xssapi.encodeForHTMLAttr(getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, null)));
            properties.put("browser-tree", xssapi.encodeForHTMLAttr(getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_TREE)));
            properties.put("browser-view", xssapi.encodeForHTMLAttr(getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_VIEW)));
            properties.put("browser-tab", xssapi.encodeForHTMLAttr(getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_VIEW, "#id#")));
            properties.put("loginUrl", xssapi.encodeForHTMLAttr(dashboardManager.getLoginUri()));
            properties.put("currentUser", xssapi.encodeForHTML(resolver.getUserID()));
            addCustomOption(browser, "style.css", properties);
            addCustomOption(browser, "script.js", properties);
            try (final InputStream pageContent = getClass().getClassLoader().getResourceAsStream(PAGE_TEMPLATE);
                 final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null;
                 final Writer writer = new ValueEmbeddingWriter(response.getWriter(), properties, Locale.ENGLISH, this.getClass())) {
                if (reader != null) {
                    response.setContentType("text/html;charset=UTF-8");
                    IOUtils.copy(reader, writer);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }
        }
    }

    public void addCustomOption(@NotNull final Resource browser, @NotNull String name,
                                @NotNull final Map<String, Object> properties) {
        Resource resource = browser.getChild("style.css");
        if (resource != null && resource.isResourceType(NT_FILE)) {
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && content.isResourceType(NT_RESOURCE)) {
                final InputStream stream = content.getValueMap().get(JCR_DATA, InputStream.class);
                if (stream != null) {
                    properties.put(name, new InputStreamReader(stream, StandardCharsets.UTF_8));
                    return;
                }
            }
        }
        final String content = browser.getValueMap().get(name, String.class);
        if (StringUtils.isNotBlank(content)) {
            properties.put(name, content);
        }
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final Resource resource = dashboardManager.getRequestResource(request);
        if (resource != null) {
            response.setContentType("text/html;charset=UTF-8");
            final String submode = getHtmlSubmode(request, Collections.singletonList(OPTION_VIEW));
            final DashboardWidget selectedView = StringUtils.isNotBlank(submode)
                    ? dashboardManager.getWidget(request, BROWSER_CONTEXT, submode) : null;
            if (selectedView != null) {
                htmlView(request, response, selectedView);
            } else {
                final Collection<DashboardWidget> browserViews = dashboardManager.getWidgets(request, BROWSER_CONTEXT);
                final PrintWriter writer = response.getWriter();
                if (StringUtils.isBlank(submode)) {
                    writer.append("<ul class=\"dashboard-browser__tabs nav nav-tabs\" role=\"tablist\">\n");
                    for (final DashboardWidget view : browserViews) {
                        final String viewId = view.getName();
                        writer.append("<li class=\"nav-item\"><a class=\"nav-link\" id=\"").append(xssapi.encodeForHTMLAttr(viewId))
                                .append("-tab\" data-toggle=\"tab\" href=\"#").append(xssapi.encodeForHTMLAttr(viewId))
                                .append("\" role=\"tab\" aria-controls=\"").append(xssapi.encodeForHTMLAttr(viewId))
                                .append("\" aria-selected=\"false\">").append(xssapi.encodeForHTML(view.getLabel()))
                                .append("</a></li>\n");
                    }
                    writer.append("</ul><div class=\"dashboard-browser__action-reload fa fa-refresh\"></div>\n");
                    writer.append("<div class=\"dashboard-browser__tabs-content tab-content\">\n");
                    for (final DashboardWidget view : browserViews) {
                        final String viewId = view.getName();
                        writer.append("<div class=\"tab-pane\" id=\"").append(xssapi.encodeForHTMLAttr(viewId))
                                .append("\" role=\"tabpanel\" aria-labelledby=\"").append(xssapi.encodeForHTMLAttr(viewId))
                                .append("-tab\">\n");
                        //htmlView(request, response, view);
                        writer.append("</div>\n");
                    }
                    writer.append("</div>\n");
                }
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public void htmlView(@NotNull final SlingHttpServletRequest request,
                         @NotNull final SlingHttpServletResponse response,
                         @NotNull final DashboardWidget view)
            throws ServletException, IOException {
        final RequestDispatcherOptions options = new RequestDispatcherOptions();
        final Resource widgetResource = view.getWidgetResource(request);
        final RequestDispatcher dispatcher = request.getRequestDispatcher(widgetResource, options);
        if (dispatcher != null) {
            dispatcher.include(request, response);
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
