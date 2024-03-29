package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.JsonRenderer;
import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.DashboardRequest;
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
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardQueryWidget.Config.class)
public class DashboardQueryWidget extends AbstractWidgetServlet implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/query";

    @ObjectClassDefinition(name = "Composum Dashboard Query Widget")
    public @interface Config {

        @AttributeDefinition(name = "Query Templates",
                description = "a set of predefined query templates offered in the query fields drop down menu")
        String[] queryTemplates();

        @AttributeDefinition(name = "Max Results")
        int maxResults() default 500;

        @AttributeDefinition(name = "max History Items")
        int historyMax() default 15;

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "query";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                DashboardBrowserServlet.BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category() default {"search", "tool"};

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 5000;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "Query";

        @AttributeDefinition(name = "Icon")
        String icon() default "search";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION)
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/find",
                DEFAULT_RESOURCE_TYPE + "/load"
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

    public static final String OPTION_FIND = "find";
    public static final String OPTION_LOAD = "load";
    public static final String OPTION_JSON = "json";

    public static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_FIND, OPTION_LOAD, OPTION_JSON);

    public static final String TEMPLATE_BASE = "/com/composum/sling/dashboard/plugin/query/";

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected ResourceFilter resourceFilter;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    protected JsonRenderer jsonRenderer;

    protected List<String> queryTemplates = new ArrayList<>();
    protected int maxResults;
    protected int historyMax;
    protected ValueMap properties = new ValueMapDecorator(new HashMap<>());

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.queryTemplates = List.of(Optional.ofNullable(config.queryTemplates()).orElse(new String[0]));
        this.maxResults = config.maxResults();
        this.historyMax = config.historyMax();
        this.properties.put("icon", config.icon());
    }

    @Override
    public <T> @NotNull T getProperty(@NotNull String name, @NotNull T defaultValue) {
        return properties.get(name, super.getProperty(name, defaultValue));
    }

    @Override
    public void embedScripts(@NotNull final ResourceResolver resolver,
                             @NotNull final PrintWriter writer, @NotNull final String mode)
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
    public void doGet(@NotNull final SlingHttpServletRequest slingRequest,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            final RequestPathInfo pathInfo = request.getRequestPathInfo();
            final String mode = getHtmlMode(request, HTML_MODES);
            if (OPTION_LOAD.equals(mode) || OPTION_JSON.equals(mode) || "json".equals(pathInfo.getExtension())) {
                if (OPTION_LOAD.equals(mode)) {
                    final Resource resource = resourceFilter.getRequestResource(request);
                    if (resource != null) {
                        response.setContentType("text/plain;charset=UTF-8");
                        jsonData(response.getWriter(), resource);
                    }
                }
            } else {
                prepareTextResponse(response, null);
                final PrintWriter writer = response.getWriter();
                switch (mode) {
                    case OPTION_FIND:
                        htmlFind(request, writer);
                        break;
                    case OPTION_TILE:
                        htmlTile(writer);
                        break;
                    case OPTION_VIEW:
                        htmlView(request, writer);
                        break;
                    case OPTION_PAGE:
                    default:
                        final ResourceResolver resolver = slingRequest.getResourceResolver();
                        htmlPageHead(resolver, writer);
                        htmlView(request, writer);
                        htmlPageTail(resolver, writer);
                        break;
                }
            }
        }
    }

    protected void htmlTile(@NotNull final PrintWriter writer)
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
                            @NotNull final PrintWriter writer)
            throws IOException {
        final String pattern = StringUtils.defaultString(request.getParameter("query"), "");
        final StringBuilder templates = new StringBuilder();
        for (String template : queryTemplates) {
            templates.append("<a class=\"dropdown-item\" href=\"#\" data-query=\"")
                    .append(xssapi.encodeForHTMLAttr(template.replace('"', '\''))).append("\">")
                    .append(xssapi.encodeForHTML(template)).append("</a>");
        }
        writer.append("<style>\n");
        copyResource(this.getClass(), TEMPLATE_BASE + "style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"dashboard-widget__query\" data-history-max=\"")
                .append(String.valueOf(historyMax)).append("\" data-popover-uri=\"")
                .append(getWidgetUri(request, resourceType, HTML_MODES, OPTION_LOAD)).append("\">");
        copyResource(getClass(), TEMPLATE_BASE + "form.html", writer, new HashMap<>() {{
            put("templates", loadTemplate("plugin/query/templates.html",
                    Collections.singletonMap("templates", templates.length() > 0
                            ? templates.toString()
                            : "<span class=\"dropdown-item\">configure your templates in the query service configuration...</span>")));
            put("action", getWidgetUri(request, resourceType, HTML_MODES, OPTION_FIND));
            put("pattern", xssapi.encodeForHTMLAttr(pattern));
        }});
        writer.append("<div class=\"dashboard-widget__query-result\">\n");
        htmlFind(request, writer);
        writer.append("</div><div class=\"dashboard-widget__query-spinner hidden\"><i class=\"fa fa-spinner fa-pulse fa-5x fa-fw\"></i></div>\n");
        writer.append("</div>\n");
    }

    protected String buildQuery(@NotNull final SlingHttpServletRequest request) {
        String pattern = StringUtils.defaultString(request.getParameter("query"), "");
        for (int i = 1; i <= 3; i++) {
            pattern = pattern.replaceAll("[$]" + i, Optional.ofNullable(
                    request.getParameter("arg" + i)).orElse(""));
        }
        return pattern;
    }

    protected void htmlFind(@NotNull final SlingHttpServletRequest request,
                            @NotNull final PrintWriter writer) {
        final String pattern = buildQuery(request);
        final JcrQuery query = StringUtils.isNotBlank(pattern) ? new JcrQuery(pattern) : null;
        writer.append("<table class=\"table table-sm table-striped\"><thead><tr class=\"query\"><th scope=\"col\" colspan=\"3\">")
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
                        writer.append("<a class=\"path\" href=\"#\" data-path=\"")
                                .append(xssapi.encodeForHTMLAttr(path)).append("\">")
                                .append(xssapi.encodeForHTML(path)).append("</a>");
                        writer.append("</td><td class=\"json\">");
                        jsonPopup(writer);
                        writer.append("</td><td class=\"type\">");
                        writer.append(xssapi.encodeForHTML(values.get("jcr:primaryType", "")));
                        writer.append("</td></tr>\n");
                    }
                }
                writer.append("<tr class=\"").append(count > maxResults ? "more" : "total")
                        .append("\"><th class=\"message\" colspan=\"3\">");
                if (count > maxResults) {
                    writer.append("More than 'max results' (").append(String.valueOf(maxResults)).append(") resources found.");
                } else {
                    writer.append(String.valueOf(count)).append(" resources found.");
                }
                writer.append("</th></tr>");
            } catch (RuntimeException ex) {
                writer.append("<tr class=\"error\"><th class=\"message\" colspan=\"3\">Exception thrown: '")
                        .append(xssapi.encodeForHTML(ex.getMessage())).append("'</th></tr>");
            }
        }
        writer.append("</tbody></table>\n");
    }

    protected void jsonPopup(@NotNull final PrintWriter writer) {
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
            jsonRenderer.dumpJson(jsonWriter, resource, 0, 0,
                    resourceFilter, jsonRenderer::isAllowedProperty, jsonRenderer::isAllowedMixin, null);
        }
    }
}
