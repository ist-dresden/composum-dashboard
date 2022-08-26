package com.composum.sling.dashboard.servlet.impl;

import com.composum.sling.dashboard.servlet.AbstractWidgetServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.impl.DashboardServiceSettingsWidget.RESOURCE_TYPE;

/**
 * a primitive viewer for the settings of a configured set of services
 */
@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Dashboard Service Settings Widget",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE,
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/view",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/tile",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/page",
                ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=html"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = DashboardServiceSettingsWidget.Config.class)
public class DashboardServiceSettingsWidget extends AbstractWidgetServlet {

    public static final String RESOURCE_TYPE = "composum/dashboard/sling/components/service/settings";

    @ObjectClassDefinition(name = "Composum Dashboard Service Settings Widget")
    public @interface Config {

        @AttributeDefinition(name = "Inspected Settings", description = "a set of request templates matching: 'service-type(filter)[service-properties,...]'")
        String[] inspectedSettings();
    }

    public static final Pattern RULE_PATTERN = Pattern.compile(
            "^(?<type>[^\\[(]+)(?<filter>\\([^)]+\\))?(\\[(?<props>.*)])?$");

    public static class SettingsRule {

        public final String serviceType;
        public final String filter;
        public final List<Pattern> properties;

        public SettingsRule(Matcher matcher) {
            serviceType = matcher.group("type");
            filter = matcher.group("filter");
            properties = new ArrayList<>();
            final String props = matcher.group("props");
            if (StringUtils.isNotBlank(props)) {
                for (String pattern : StringUtils.split(props, ",")) {
                    properties.add(Pattern.compile(pattern));
                }
            }
        }
    }

    @Reference
    protected XSSAPI xssapi;

    protected transient List<SettingsRule> configuration;

    protected transient BundleContext bundleContext;

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        this.bundleContext = bundleContext;
        configuration = new ArrayList<>();
        for (final String rule : config.inspectedSettings()) {
            if (StringUtils.isNotBlank(rule)) {
                Matcher matcher = RULE_PATTERN.matcher(rule);
                if (matcher.matches()) {
                    configuration.add(new SettingsRule(matcher));
                }
            }
        }
    }

    protected @NotNull List<ServiceReference<?>> getServiceReferences(@NotNull final SettingsRule config) {
        final List<ServiceReference<?>> serviceReferences = new ArrayList<>();
        try {
            ServiceReference<?>[] references = bundleContext.getAllServiceReferences(config.serviceType,
                    StringUtils.isNotBlank(config.filter) ? config.filter : null);
            if (references != null) {
                serviceReferences.addAll(Arrays.asList(references));
            } else {
                ServiceReference<?>[] all = bundleContext.getAllServiceReferences(null,
                        StringUtils.isNotBlank(config.filter) ? config.filter : null);
                for (ServiceReference<?> ref : all) {
                    if (config.serviceType.equals(ref.getProperty("service.pid"))
                            || (!config.serviceType.contains("~")
                            && config.serviceType.equals(ref.getProperty("service.factoryPid")))) {
                        serviceReferences.add(ref);
                        break;
                    }
                }
            }
        } catch (InvalidSyntaxException ignore) {
        }
        return serviceReferences;
    }

    /**
     * @return the service properties, 'null' if the service is not available
     */
    protected @Nullable Map<String, Object> getProperties(@NotNull final ServiceReference<?> reference,
                                                          @Nullable final List<Pattern> patterns) {
        Map<String, Object> properties = null;
        final Object service = getService(reference);
        properties = new LinkedHashMap<>();
        for (final String name : reference.getPropertyKeys()) {
            if (patterns == null || patterns.isEmpty()) {
                properties.put(name, getProperty(reference, service, name));
            } else {
                boolean found = false;
                for (final Pattern pattern : patterns) {
                    final Matcher matcher = pattern.matcher(name);
                    if (matcher.matches()) {
                        properties.put(name, getProperty(reference, service, name));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                }
            }
        }
        if (service != null && patterns != null) {
            for (final Pattern pattern : patterns) {
                String name = pattern.toString();
                if (!properties.containsKey(name)) {
                    Object value = getProperty(reference, service, name);
                    if (value != null) {
                        properties.put(name, getProperty(reference, service, name));
                    }
                }
            }
        }
        return properties;
    }


    public @Nullable Object getProperty(@NotNull final ServiceReference<?> reference, @Nullable final Object service,
                                        @NotNull final String name) {
        Object value = null;
        if (StringUtils.isNotBlank(name)) {
            value = reference.getProperty(name);
            if (value == null && service != null) {
                try {
                    Method getter = service.getClass().getMethod(
                            "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                    value = getter.invoke(service);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
                }
            }
        }
        return value;
    }

    protected @Nullable Object getService(@Nullable final ServiceReference<?> reference) {
        if (reference != null) {
            return bundleContext.getService(reference);
        }
        return null;
    }

    protected boolean isServiceActive(@NotNull final ServiceReference<?> reference) {
        return getService(reference) != null;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final PrintWriter writer = response.getWriter();
        final String mode = getHtmlMode(request, HTML_MODES);
        response.setContentType("text/html;charset=UTF-8");
        switch (mode) {
            case OPTION_TILE:
                htmlTile(request, response, writer);
                break;
            case OPTION_VIEW:
            default:
                htmlView(request, response, writer);
                break;
            case OPTION_PAGE:
                htmlPageHead(writer, "Service Settings");
                htmlView(request, response, writer);
                htmlPageTail(writer);
                break;
        }
    }

    protected void htmlTile(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/service/settings/style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"card dashboard-widget__settings-tile\"><div class=\"card-header bg-")
                .append("info".replace("info", "primary"))
                .append(" text-white\">").append("Service Settings")
                .append("</div><ul class=\"list-group list-group-flush\">\n");
        for (final SettingsRule config : configuration) {
            for (final ServiceReference<?> reference : getServiceReferences(config)) {
                final String label = xssapi.encodeForHTML(getSimpleServiceName(config.serviceType));
                final boolean active = isServiceActive(reference);
                writer.append("<li class=\"list-group-item d-flex justify-content-between\">")
                        .append(label).append("<span class=\"service-status badge badge-pill badge-")
                        .append(active ? "success" : "danger").append("\"><i class=\"fa fa-")
                        .append(active ? "check" : "times").append("\"></i></span></li>\n");
            }
        }
        writer.append("</ul></div>\n");
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/service/settings/style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"dashboard-widget__settings-view\">");
        for (final SettingsRule config : configuration) {
            for (final ServiceReference<?> reference : getServiceReferences(config)) {
                final String domId = domId(config.serviceType);
                final boolean active = isServiceActive(reference);
                writer.append("<div class=\"card\"><div class=\"card-header\" id=\"card-")
                        .append(domId).append("\">")
                        .append("<button class=\"btn btn-link d-flex justify-content-between\" data-toggle=\"collapse\" data-target=\"#pane-")
                        .append(domId).append("\" aria-controls=\"pane-").append(domId).append("\"><span>")
                        .append(xssapi.encodeForHTML(config.serviceType)).append("</span><span class=\"service-status badge badge-pill badge-")
                        .append(active ? "success" : "danger").append("\"><i class=\"fa fa-")
                        .append(active ? "check" : "times").append("\"></i></span></button></div><div class=\"card-body\">\n");
                writer.append("<div id=\"pane-").append(domId)
                        .append("\" class=\"collapse\" aria-labelledby=\"card-").append(domId).append("\">\n");
                writer.append("<table class=\"table table-sm table-striped\"><tbody>\n");
                Map<String, Object> properties = Optional.ofNullable(getProperties(reference, config.properties))
                        .orElse(Collections.emptyMap());
                for (final Map.Entry<String, Object> entry : properties.entrySet()) {
                    writer.append("<tr><td>").append(xssapi.encodeForHTML(entry.getKey())).append("</td><td>")
                            .append(xssapi.encodeForHTML(propertyString(entry.getValue())))
                            .append("</td></tr>\n");
                }
                writer.append("</tbody></table></div>\n");
                writer.append("</div></div>\n");
            }
        }
        writer.append("</div>\n");
    }

    protected @NotNull String propertyString(@Nullable final Object value) {
        return propertyString(new StringBuilder(), value).toString();
    }

    protected @NotNull StringBuilder propertyString(@NotNull final StringBuilder builder, @Nullable final Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof Object[]) {
            final Object[] array = (Object[]) value;
            builder.append("[ ");
            for (int i = 0; i < array.length; ) {
                propertyString(builder, array[i]);
                if (++i < array.length) {
                    builder.append(", ");
                }
            }
            builder.append(" ]");
        } else {
            builder.append(value);
        }
        return builder;
    }

    protected @NotNull String getSimpleServiceName(@NotNull final String serviceType) {
        return StringUtils.substringAfterLast(serviceType, ".");
    }

    protected @NotNull String domId(@NotNull final String serviceType) {
        return serviceType.replaceAll("[^a-zA-Z0-9_-]", "-");
    }
}
