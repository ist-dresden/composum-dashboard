package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.Properties;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * a primitive viewer for the settings of a configured set of services
 */
public abstract class AbstractSettingsWidget extends AbstractWidgetServlet {

    protected abstract class SettingsProvider {

        protected Map<String, Object> properties;

        public abstract String getName();

        public abstract String getLabel();

        public abstract boolean isAvailable();

        public abstract @NotNull Iterable<String> getPropertyNames();

        public abstract @Nullable Object getProperty(@NotNull String name);

        public @NotNull Map<String, Object> getProperties() {
            if (properties == null) {
                properties = new TreeMap<>();
                for (String name : getPropertyNames()) {
                    addProperty(properties, name);
                }
            }
            return properties;
        }

        protected void addProperty(@NotNull final Map<String, Object> properties, @NotNull final String name) {
            if (StringUtils.isNotBlank(name)) {
                Object value = getProperty(name);
                if (value != null) {
                    properties.put(name, value);
                }
            }
        }

        protected @Nullable Object getProperty(@Nullable final Object object, @Nullable final String name) {
            Object value = null;
            if (object != null && StringUtils.isNotBlank(name)) {
                final Class<?> objectClass = object.getClass();
                Method getter;
                try {
                    try {
                        getter = objectClass.getMethod(name);
                        value = getter.invoke(object);
                    } catch (NoSuchMethodException ignore) {
                        getter = objectClass.getMethod(
                                "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                        value = getter.invoke(object);
                    }
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
                }
            }
            return value;
        }
    }

    protected static final String OPTION_JSON = "json";

    public static final Pattern PROPERTY_NAME = Pattern.compile("^[a-zA-Z0-9$@_:.-]+$");

    protected abstract @NotNull String getClientlibPath();

    protected abstract @NotNull List<SettingsProvider> getSettingsProviders(@NotNull SlingHttpServletRequest request);

    protected abstract @NotNull ResourceFilter resourceFilter();

    protected abstract @NotNull XSSAPI xssApi();

    protected abstract @NotNull List<String> getHtmlModes();

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final Resource resource = Optional.ofNullable(resourceFilter().getRequestResource(request)).orElse(request.getResource());
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final String mode = getHtmlMode(request, getHtmlModes());
        if (!OPTION_JSON.equals(mode) && !"json".equals(pathInfo.getExtension())) {
            prepareHtmlResponse(response);
            final PrintWriter writer = response.getWriter();
            switch (mode) {
                case OPTION_TILE:
                    htmlTile(request, response, writer);
                    break;
                case OPTION_VIEW:
                    htmlView(request, response, writer, resource);
                    break;
                case OPTION_PAGE:
                default:
                    htmlPageHead(writer);
                    htmlView(request, response, writer, resource);
                    htmlPageTail(writer);
                    break;
            }
        } else {
            response.setContentType("application/json;charset=UTF-8");
            final JsonWriter writer = new JsonWriter(response.getWriter());
            dumpJson(request, response, writer);
        }
    }

    protected void htmlTile(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), getClientlibPath() + "/style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"card dashboard-widget__settings-tile\"><div class=\"card-header bg-")
                .append("info".replace("info", "primary"))
                .append(" text-white\">").append(getLabel())
                .append("</div><ul class=\"list-group list-group-flush\">\n");
        for (final SettingsProvider provider : getSettingsProviders(request)) {
            final String label = xssApi().encodeForHTML(provider.getLabel());
            final boolean active = provider.isAvailable();
            writer.append("<li class=\"list-group-item d-flex justify-content-between\">")
                    .append(label).append("<span class=\"service-status badge badge-pill badge-")
                    .append(active ? "success" : "danger").append("\"><i class=\"fa fa-")
                    .append(active ? "check" : "times").append("\"></i></span></li>\n");
        }
        writer.append("</ul></div>\n");
        writer.append("<script>\n");
        copyResource(this.getClass(), getClientlibPath() + "/script.js", writer);
        writer.append("</script>\n");
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer, @NotNull final Resource context)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/service/settings/style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"dashboard-widget__settings-view\">");
        int index = 0;
        for (final SettingsProvider provider : getSettingsProviders(request)) {
            final String name = provider.getName();
            final String domId = domId(name);
            final boolean available = provider.isAvailable();
            writer.append("<div class=\"card\"><div class=\"card-header\" id=\"card-")
                    .append(domId).append("\">")
                    .append("<button class=\"btn btn-link d-flex justify-content-between\" data-toggle=\"collapse\" data-target=\"#pane-")
                    .append(domId).append("\" aria-controls=\"pane-").append(domId).append("\" aria-expanded=\"")
                    .append(Boolean.toString(index == 0)).append("\"><span>").append(xssApi().encodeForHTML(name))
                    .append("</span><span class=\"service-status badge badge-pill badge-")
                    .append(available ? "success" : "danger").append("\"><i class=\"fa fa-")
                    .append(available ? "check" : "times").append("\"></i></span></button></div><div class=\"card-body\">\n");
            writer.append("<div id=\"pane-").append(domId).append("\" class=\"collapse").append(index == 0 ? " show" : "")
                    .append("\" aria-labelledby=\"card-").append(domId).append("\">\n");
            writer.append("<table class=\"table table-sm table-striped\"><tbody>\n");
            for (final Map.Entry<String, Object> entry : provider.getProperties().entrySet()) {
                writer.append("<tr><td class=\"name\">").append(xssApi().encodeForHTML(entry.getKey()))
                        .append("</td><td class=\"value\">");
                String type = Properties.toHtml(writer, context, name, entry.getValue(), resourceFilter(), xssApi());
                writer.append("</td><td class=\"type\">").append(xssApi().encodeForHTML(type)).append("</td></tr>\n");
            }
            writer.append("</tbody></table></div>\n");
            writer.append("</div></div>\n");
            index++;
        }
        writer.append("</div>\n");
    }

    protected @NotNull String domId(@NotNull final String serviceType) {
        return serviceType.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    protected void dumpJson(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final JsonWriter writer)
            throws IOException {
        writer.beginArray();
        for (final SettingsProvider provider : getSettingsProviders(request)) {
            writer.beginObject();
            writer.name("label").value(provider.getLabel());
            writer.name("name").value(provider.getName());
            writer.name("available").value(provider.isAvailable());
            writer.name("properties").beginObject();
            for (final Map.Entry<String, Object> entry : provider.getProperties().entrySet()) {
                writer.name(entry.getKey());
                Properties.toJson(writer, entry.getValue(), JSON_DATE_FORMAT);
            }
            writer.endObject();
            writer.endObject();
        }
        writer.endArray();
    }
}
