package com.composum.sling.dashboard.servlet.impl;

import com.composum.sling.dashboard.service.DashboardBrowser;
import com.composum.sling.dashboard.servlet.AbstractWidgetServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
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
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.impl.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Dashboard Properies View",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = DashboardPropertiesView.Config.class)
public class DashboardPropertiesView extends AbstractWidgetServlet {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/components/properties";

    @ObjectClassDefinition(name = "Composum Dashboard Properies View")
    public @interface Config {

        @AttributeDefinition(name = "Context")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String category();

        @AttributeDefinition(name = "Rank")
        int rank() default 500;

        @AttributeDefinition(name = "Label")
        String label() default "JSON";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Allowed Property Patterns")
        String[] allowedPropertyPatterns() default {
                "^.*$"
        };

        @AttributeDefinition(name = "Disabled Property Patterns")
        String[] disabledPropertyPatterns() default {
                "^rep:.*$",
                "^.*password.*$"
        };

        @AttributeDefinition(name = "Servlet Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view"
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

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected DashboardBrowser browser;

    protected List<Pattern> allowedPropertyPatterns;
    protected List<Pattern> disabledPropertyPatterns;

    @Activate
    @Modified
    protected void activate(DashboardPropertiesView.Config config) {
        super.activate(config.context(), config.category(), config.rank(), config.label(), config.navTitle(),
                config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        allowedPropertyPatterns = patternList(config.allowedPropertyPatterns());
        disabledPropertyPatterns = patternList(config.disabledPropertyPatterns());
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    protected boolean isAllowedProperty(@NotNull final String name) {
        for (Pattern allowed : allowedPropertyPatterns) {
            if (allowed.matcher(name).matches()) {
                for (Pattern disabled : disabledPropertyPatterns) {
                    if (disabled.matcher(name).matches()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final Resource resource = browser.getRequestResource(request);
        if (resource != null) {
            ResourceResolver resolver = resource.getResourceResolver();
            response.setContentType("text/html;charset=UTF-8");
            final PrintWriter writer = response.getWriter();
            writer.append("<table class=\"table table-sm table-striped\"><thead><tr>\n" +
                    "      <th scope=\"col\">Name</th>\n" +
                    "      <th scope=\"col\" width=\"100%\">Value</th>\n" +
                    "      <th scope=\"col\">Type</th>\n" +
                    "    </tr></thead><tbody>\n");
            final TreeMap<String, Object> properties = new TreeMap<>(resource.getValueMap());
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                final String name = entry.getKey();
                if (isAllowedProperty(name)) {
                    final Object value = entry.getValue();
                    writer.append("<tr><td>").append(xssapi.encodeForHTML(name)).append("</td><td>");
                    final String type = writeValue(writer, resource, name, value, resolver);
                    writer.append("</td>").append("<td>").append(type).append("</td></tr>\n");
                }
            }
            writer.append("</tbody></table>\n");
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public String writeValue(@NotNull final PrintWriter writer, @NotNull final Resource resource,
                             @NotNull final String name, @Nullable final Object value,
                             @NotNull final ResourceResolver resolver) {
        String type = "";
        if (value != null) {
            if (value instanceof Object[]) {
                writer.append("<ul>");
                for (Object val : (Object[]) value) {
                    writer.append("<li>");
                    type = writeValue(writer, resource, name, val, resolver);
                    writer.append("</li>");
                }
                writer.append("</ul>");
                type += "[]";
            } else if (value instanceof Calendar) {
                writer.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").format(((Calendar) value).getTime()));
                type = "Date";
            } else if (value instanceof InputStream) {
                writer.append("<a class=\"binary\" href=\"")
                        .append(StringUtils.substringBeforeLast(resource.getPath(), "/jcr:content"))
                        .append("\">download...</a>");
                type = "Binary";
            } else if (value instanceof String) {
                final String string = (String) value;
                Resource target = resolvePath(resolver, string);
                if (target == null) {
                    target = resolveType(resolver, string);
                }
                if (target != null) {
                    writer.append("<a class=\"path\" href=\"").append(target.getPath()).append("\">")
                            .append(xssapi.encodeForHTML(string)).append("</a>");
                } else {
                    writer.append(xssapi.encodeForHTML(string));
                }
                type = "String";
            } else {
                writer.append(xssapi.encodeForHTML(value.toString()));
                type = value.getClass().getSimpleName();
            }
        }
        return type;
    }

    protected @Nullable Resource resolveType(@NotNull final ResourceResolver resolver, @Nullable final String type) {
        if (StringUtils.isNotBlank(type) && StringUtils.countMatches(type, "/") > 1) {
            Resource resource;
            for (String root : resolver.getSearchPath()) {
                resource = resolvePath(resolver, root + type);
                if (resource != null) {
                    return resource;
                }
            }
        }
        return null;
    }

    protected @Nullable Resource resolvePath(@NotNull final ResourceResolver resolver, @Nullable final String path) {
        if (StringUtils.isNotBlank(path) && path.startsWith("/")) {
            final Resource resource = resolver.getResource(path);
            if (resource != null && browser.isAllowedResource(resource)) {
                return resource;
            }
        }
        return null;
    }
}
