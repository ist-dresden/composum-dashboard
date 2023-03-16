package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.TraceManager;
import com.composum.sling.dashboard.service.TraceService;
import com.composum.sling.dashboard.service.TraceService.Level;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.google.gson.stream.JsonWriter;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
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
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static com.composum.sling.dashboard.servlet.DashboardServlet.DASHBOARD_CONTEXT;

/**
 * a primitive logfile viewer servlet implementation to declare a Composum Dashborad Widget for logfiles
 */
@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardTraceWidget.Config.class)
public class DashboardTraceWidget extends AbstractWidgetServlet implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/trace";

    @ObjectClassDefinition(name = "Composum Dashboard Trace Widget")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "trace";

        @AttributeDefinition(name = "Context")
        String[] context() default {
                DASHBOARD_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category();

        @AttributeDefinition(name = "Rank")
        int rank() default 5000;

        @AttributeDefinition(name = "Label")
        String label() default "Trace";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Resource Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/tile"
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

    public static final Map<Level, String> LEVEL_TO_BADGE = new HashMap<>() {{
        put(Level.ERROR, "danger");
        put(Level.WARNING, "warning");
        put(Level.SUCCESS, "success");
        put(Level.INFO, "primary");
        put(Level.DEBUG, "secondary");
    }};

    @Reference
    protected TraceManager traceManager;

    @Reference
    protected XSSAPI xssapi;

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
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    /*
    protected static int counter = 0;

    protected void simulateTrace() {
        Level level = new Level[]{Level.ERROR, Level.WARNING, Level.SUCCESS, Level.INFO, Level.DEBUG}
                [(int) Math.round(Math.random() * 4)];
        traceManager.trace(null, level, null, "simulated trace of level '%s'", level,
                new HashMap<>() {{
                    put("levelHint", "# " + (++counter));
                    put("list", new String[]{"item 0", "item 1"});
                }});
    }
    */

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest slingRequest,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            //simulateTrace();
            final PrintWriter writer = response.getWriter();
            final String mode = getHtmlMode(request, HTML_MODES);
            final RequestPathInfo pathInfo = request.getRequestPathInfo();
            if ("json".equals(pathInfo.getExtension())) {

            } else {
                switch (mode) {
                    case OPTION_TILE:
                        htmlTile(request, response, writer);
                        return;
                    case OPTION_VIEW:
                        htmlView(request, response, writer);
                        return;
                    case OPTION_PAGE:
                    default:
                        prepareTextResponse(response, null);
                        htmlPageHead(writer);
                        htmlView(request, response, writer);
                        htmlPageTail(writer, "/com/composum/sling/dashboard/commons/script.js");
                        return;
                }
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    protected void htmlTile(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/logfile/style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"card dashboard-widget__logfile-tile\"><div class=\"card-header bg-")
                .append("info".replace("info", "primary"))
                .append(" text-white\">").append(getLabel())
                .append("</div><ul class=\"list-group list-group-flush\">\n");
        for (final TraceService trace : traceManager.getTraces()) {
            writer.append("<li class=\"list-group-item d-flex justify-content-between\">")
                    .append(trace.getLabel()).append("<span>");
            htmlBadges(writer, trace);
            writer.append("</span></li>");
        }
        writer.append("</ul></div>\n");
    }

    protected void htmlBadges(@NotNull final PrintWriter writer, @NotNull final TraceService trace) {
        writer.append("<span class=\"dashboard-widget__badges\">");
        htmlBadge(writer, "danger", trace.getNumber(Level.ERROR));
        htmlBadge(writer, "warning", trace.getNumber(Level.WARNING));
        htmlBadge(writer, "success", trace.getNumber(Level.SUCCESS));
        htmlBadge(writer, "primary", trace.getNumber(Level.INFO));
        htmlBadge(writer, "secondary", trace.getNumber(Level.DEBUG));
        writer.append("</span>\n");
    }

    protected void htmlBadge(@NotNull final PrintWriter writer, @NotNull final String level, int number) {
        if (number > 0) {
            writer.append("&nbsp;<i class=\"badge badge-pill badge-").append(level).append("\">")
                    .append(String.valueOf(number)).append("</i>");
        }
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/trace/style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"dashboard-widget__trace-view resuming-tabs\" data-tabs-id=\"trace\">\n");
        if (traceManager.getTraceNumber() > 1) {
            writer.append("<ul class=\"resuming-tabs_nav nav nav-tabs\" role=\"tablist\">\n");
            int index = 0;
            for (TraceService trace : traceManager.getTraces()) {
                final String tabId = xssapi.encodeForHTMLAttr(trace.getName());
                writer.append("<li class=\"resuming-tabs_nav-item nav-item\"><a class=\"nav-link")
                        .append(index == 0 ? " active" : "").append("\" id=\"tab-").append(tabId)
                        .append("\" data-toggle=\"tab\" href=\"#panel-").append(tabId)
                        .append("\" role=\"tab\" aria-controls=\"panel-").append(tabId)
                        .append("\" aria-selected=\"").append(index == 0 ? "true" : "false").append("\">")
                        .append(xssapi.encodeForHTML(trace.getLabel()));
                htmlBadges(writer, trace);
                writer.append("</a></li>\n");
                index++;
            }
            writer.append("</ul>\n");
        }
        if (traceManager.getTraceNumber() > 1) {
            writer.append("<div class=\"resuming-tabs_content tab-content\">\n");
        }
        int index = 0;
        for (TraceService trace : traceManager.getTraces()) {
            if (traceManager.getTraceNumber() > 1) {
                final String tabId = xssapi.encodeForHTMLAttr(trace.getName());
                writer.append("<div class=\"resuming-tabs_pane tab-pane fade")
                        .append(index == 0 ? " show active" : "").append("\" id=\"panel-").append(tabId)
                        .append("\" role=\"tabpanel\" aria-labelledby=\"tab-").append(tabId)
                        .append("\">\n");
            }
            traceView(request, response, trace, writer);
            if (traceManager.getTraceNumber() > 1) {
                writer.append("</div>\n");
            }
            index++;
        }
        if (traceManager.getTraceNumber() > 1) {
            writer.append("</div>\n");
        }
        writer.append("</div>\n");
        writer.append("<script>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/trace/script.js", writer);
        writer.append("</script>\n");
    }

    protected void traceView(@NotNull final SlingHttpServletRequest request,
                             @NotNull final SlingHttpServletResponse response,
                             @NotNull final TraceService trace, @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<div class=\"dashboard-widget__trace\">");
        int index = 0;
        for (final TraceService.TraceEntry entry : trace.getEntries(null)) {
            final Level level = entry.getLevel();
            final String domId = "entry-" + index;
            writer.append("<div class=\"card\"><div class=\"card-header\" id=\"card-")
                    .append(domId).append("\">")
                    .append("<button class=\"btn btn-link d-flex\" data-toggle=\"collapse\" data-target=\"#pane-")
                    .append(domId).append("\" aria-controls=\"pane-").append(domId).append("\" aria-expanded=\"false\">");
            writer.append("<span class=\"trace-time\">").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(entry.getTime())).append("</span>");
            writer.append("<span class=\"trace-level badge badge-pill badge-").append(LEVEL_TO_BADGE.get(level)).append("\">")
                    .append(xssapi.encodeForHTMLAttr(entry.getProperty("levelHint", level.name()))).append("</span>");
            writer.append("<span>").append(xssapi.encodeForHTML(entry.getMessage())).append("</span>");
            writer.append("</button></div><div class=\"card-body\">\n");
            writer.append("<div id=\"pane-").append(domId).append("\" class=\"collapse\" aria-labelledby=\"card-").append(domId).append("\">\n");
            writer.append("<textarea readonly=\"readonly\">");
            final JsonWriter jsonWriter = new JsonWriter(writer);
            jsonWriter.setIndent("  ");
            entry.toJson(jsonWriter);
            jsonWriter.flush();
            writer.append("</textarea></div></div></div>\n");
            index++;
        }
        writer.append("</div>\n");
    }
}