package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.JsonRenderer;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.JcrQuery;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.xss.XSSAPI;
import org.apache.sling.xss.XSSFilter;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

@Component(service = {Servlet.class, DashboardWidget.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardQueryWidget.Config.class)
public class DashboardQueryWidget extends AbstractWidgetServlet {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/query";

    @ObjectClassDefinition(name = "Composum Dashboard Query Widget")
    public @interface Config {

        @AttributeDefinition(name = "Max Results")
        int maxResults() default 500;

        @AttributeDefinition(name = "Name")
        String name() default "query";

        @AttributeDefinition(name = "Context")
        String[] context() default {
                DashboardBrowserServlet.BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category() default {"search"};

        @AttributeDefinition(name = "Rank")
        int rank() default 5000;

        @AttributeDefinition(name = "Label")
        String label() default "Query";

        @AttributeDefinition(name = "Icon")
        String icon() default "search";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Servlet Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/find",
                DEFAULT_RESOURCE_TYPE + "/load"
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

    public static final String OPTION_FIND = "find";
    public static final String OPTION_LOAD = "load";
    public static final String OPTION_JSON = "json";

    public static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_FIND, OPTION_LOAD, OPTION_JSON);

    public static final String TEMPLATE_BASE = "/com/composum/sling/dashboard/plugin/query/";

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected XSSFilter xssFilter;

    @Reference
    protected ResourceFilter resourceFilter;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    protected JsonRenderer jsonRenderer;

    protected int maxResults;
    protected ValueMap properties = new ValueMapDecorator(new HashMap<>());

    @Activate
    @Modified
    protected void activate(Config config) {
        super.activate(config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.maxResults = config.maxResults();
        properties.put("icon", config.icon());
    }

    @Override
    public <T> @NotNull T getProperty(@NotNull String name, @NotNull T defaultValue) {
        return properties.get(name, super.getProperty(name, defaultValue));
    }

    @Override
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode)
            throws IOException {
        if (OPTION_PAGE.equals(mode) || OPTION_VIEW.equals(mode)) {
            writer.append("<script>\n");
            copyResource(this.getClass(), TEMPLATE_BASE + "script.js", writer);
            writer.append("</script>\n");
        }
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final String mode = getHtmlMode(request, HTML_MODES);
        if (OPTION_LOAD.equals(mode) || OPTION_JSON.equals(mode) || "json".equals(pathInfo.getExtension())) {
            if (OPTION_LOAD.equals(mode)) {
                final Resource resource = resourceFilter.getRequestResource(request);
                if (resource != null) {
                    response.setContentType("text/plain;charset=UTF-8");
                    jsonData(response.getWriter(), resource);
                }
            } else {
                response.setContentType("application/json;charset=UTF-8");
                final JsonWriter writer = new JsonWriter(response.getWriter());
                //jsonFind(request, response, writer);
            }
        } else {
            response.setContentType("text/html;charset=UTF-8");
            final PrintWriter writer = response.getWriter();
            switch (mode) {
                case OPTION_FIND:
                    htmlFind(request, response, writer);
                    break;
                case OPTION_TILE:
                    htmlTile(request, response, writer);
                    break;
                case OPTION_VIEW:
                    htmlView(request, response, writer);
                    break;
                case OPTION_PAGE:
                default:
                    htmlPageHead(writer);
                    htmlView(request, response, writer);
                    htmlPageTail(writer);
                    break;
            }
        }
    }

    protected void htmlTile(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), TEMPLATE_BASE + "style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"card dashboard-widget__query\"><div class=\"card-header bg-")
                .append("info".replace("info", "primary"))
                .append(" text-white\">").append(getLabel())
                .append("</div><ul class=\"list-group list-group-flush\">\n");
        writer.append("</ul></div>\n");
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        final String pattern = StringUtils.defaultString(request.getParameter("query"), "");
        writer.append("<style>\n");
        copyResource(this.getClass(), TEMPLATE_BASE + "style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"dashboard-widget__query\" data-popover-uri=\"")
                .append(getWidgetUri(request, resourceType, HTML_MODES, OPTION_LOAD)).append("\">");
        copyResource(getClass(), TEMPLATE_BASE + "form.html", writer, new HashMap<>() {{
            put("action", getWidgetUri(request, resourceType, HTML_MODES, OPTION_FIND));
            put("pattern", xssapi.encodeForHTMLAttr(pattern));
        }});
        writer.append("<div class=\"dashboard-widget__query-result\">\n");
        htmlFind(request, response, writer);
        writer.append("</div></div>\n");
    }

    protected void htmlFind(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer) {
        final String pattern = xssFilter.filter(StringUtils
                        .defaultString(request.getParameter("query"), ""))
                .replaceAll("&quot;", "\"")
                .replaceAll("\"(.*)&amp;(.*)\"", "\"$1&$2\"");
        final JcrQuery query = StringUtils.isNotBlank(pattern) ? new JcrQuery(pattern) : null;
        writer.append("<table class=\"table table-sm table-striped\"><thead><tr><th scope=\"col\" colspan=\"3\">")
                .append(xssapi.encodeForHTML(query != null ? query.getQuery() : ""))
                .append("</th></tr></thead><tbody>\n");
        if (query != null) {
            try {
                final ResourceResolver resolver = request.getResourceResolver();
                final Iterator<Resource> found = query.find(resolver);
                int count = 0;
                while (found.hasNext()) {
                    final Resource resource = found.next();
                    if (resourceFilter.isAllowedResource(resource)) {
                        if (++count > maxResults) {
                            break;
                        }
                        final String path = resource.getPath();
                        final ValueMap values = resource.getValueMap();
                        writer.append("<tr><td class=\"path\" width=\"100%\">");
                        writer.append("<a class=\"path\" href=\"").append(xssapi.encodeForHTMLAttr(path)).append("\">")
                                .append(xssapi.encodeForHTML(path)).append("</a>");
                        writer.append("</td><td class=\"json\">");
                        jsonPopup(writer, resource);
                        writer.append("</td><td class=\"type\">");
                        writer.append(xssapi.encodeForHTML(values.get("jcr:primaryType", "")));
                        writer.append("</td></tr>\n");
                    }
                }
                writer.append("<tr class\"more\"><th class=\"message\" colspan=\"3\">");
                if (count > maxResults) {
                    writer.append("More than 'max results' (").append(String.valueOf(maxResults)).append(") resources found.");
                } else {
                    writer.append(String.valueOf(count)).append(" resources found.");
                }
                writer.append("</th></tr>");
            } catch (RuntimeException ex) {
                writer.append("<tr class\"error\"><th class=\"message\" colspan=\"3\">Exception thrown: '")
                        .append(xssapi.encodeForHTML(ex.getMessage())).append("'</th></tr>");
            }
        }
        writer.append("</tbody></table>\n");
    }

    protected void jsonPopup(@NotNull final PrintWriter writer, @NotNull final Resource resource) {
        if (jsonRenderer != null) {
            writer.append("<button class=\"btn btn-sm\" data-toggle=\"popover\" data-trigger=\"focus\" data-placement=\"left\"");
            try (final StringWriter buffer = new StringWriter()) {
                writer.append(" data-content=\"<pre>");
                //jsonData(writer, resource);
                writer.append(xssapi.encodeForHTMLAttr(buffer.toString()));
                writer.append("</pre>\"");
            } catch (IOException ignore) {
            }
            writer.append(">...</button>");
        }
    }

    protected void jsonData(@NotNull final PrintWriter writer, @NotNull final Resource resource)
            throws IOException {
        if (jsonRenderer != null) {
            writer.append(resource.getPath()).append("\n");
            JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setIndent("  ");
            jsonRenderer.dumpJson(jsonWriter, resource, 0, 0);
        }
    }
}
