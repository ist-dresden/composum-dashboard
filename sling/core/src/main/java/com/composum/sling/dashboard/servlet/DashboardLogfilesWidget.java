package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.settings.SlingSettingsService;
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

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.DashboardServlet.DASHBOARD_CONTEXT;

/**
 * a primitive logfile viewer servlet implementation to declare a Composum Dashborad Widget for logfiles
 */
@Component(service = {Servlet.class, DashboardWidget.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardLogfilesWidget.Config.class)
public class DashboardLogfilesWidget extends AbstractWidgetServlet {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/logfiles";

    @ObjectClassDefinition(name = "Composum Dashboard Logfile Widget")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "logfiles";

        @AttributeDefinition(name = "Context")
        String[] context() default {
                DASHBOARD_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category();

        @AttributeDefinition(name = "Rank")
        int rank() default 5000;

        @AttributeDefinition(name = "Label")
        String label() default "Logfiles";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Logfiles")
        String[] logFiles() default {
                "/logs/error.log"
        };

        @AttributeDefinition(name = "Error Pattern")
        String errorPattern() default "\\*ERROR\\*";

        @AttributeDefinition(name = "Warning Pattern")
        String warningPattern() default "\\*WARN\\*";

        @AttributeDefinition(name = "Size Limit (Kb)")
        int sizeLimit() default 5000;

        @AttributeDefinition(name = "Servlet Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/tail",
                DEFAULT_RESOURCE_TYPE + "/tile"
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

    public class LoggerSession implements Serializable {

        private final String logfile;
        private final File file;
        private final Pattern errorPattern;
        private final Pattern warningPattern;
        private final long sizeLimit;

        private long lastSummaryPosition = 0;
        private long lastDumpPosition = 0;

        private int lines = 0;
        private int errors = 0;
        private int warnings = 0;

        public LoggerSession(@NotNull final String logfile, @NotNull final File file,
                             @NotNull final Pattern errorPattern, @NotNull final Pattern warningPattern,
                             int sizeLimit) {
            this.logfile = logfile;
            this.file = file;
            this.errorPattern = errorPattern;
            this.warningPattern = warningPattern;
            this.sizeLimit = sizeLimit * 1000L;
        }

        public @NotNull String getLogfile() {
            return logfile;
        }

        public int getLines() {
            return lines;
        }

        public int getErrors() {
            return errors;
        }

        public int getWarnings() {
            return warnings;
        }

        public void summarize() {
            lastSummaryPosition = tail(null, lastSummaryPosition);
        }

        public void dump(@NotNull final PrintWriter writer, boolean reset) {
            lastDumpPosition = tail(writer, reset ? 0 : lastDumpPosition);
        }

        public synchronized long tail(@Nullable final PrintWriter writer, long position) {
            final long length = file.length();
            if (position < length) {
                try (final RandomAccessFile fileAccess = new RandomAccessFile(file, "r")) {
                    long limitStart = position > 0 ? position : Math.max(length - sizeLimit, 0);
                    while (limitStart > 0) {
                        fileAccess.seek(--limitStart);
                        if (fileAccess.readByte() == 0xA) {
                            limitStart++;
                            break;
                        }
                    }
                    if (limitStart > position) {
                        position = limitStart;
                    }
                    fileAccess.seek(position);
                    String line;
                    while ((line = fileAccess.readLine()) != null) {
                        if (writer != null) {
                            writer.println(line);
                        } else {
                            lines++;
                            if (errorPattern.matcher(line).find()) {
                                errors++;
                            }
                            if (warningPattern.matcher(line).find()) {
                                warnings++;
                            }
                        }
                    }
                    return fileAccess.getFilePointer();
                } catch (IOException ignore) {
                    return 0;
                }
            }
            return position;
        }
    }

    protected static final String OPTION_TAIL = "tail";
    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_TAIL, OPTION_TILE);

    public static final String SA_SESSIONS = DashboardLogfilesWidget.class.getName() + "#sessions";

    @Reference
    protected SlingSettingsService slingSettingsService;

    @Reference
    protected XSSAPI xssapi;

    protected String slingHomePath;
    protected String slingHomeName;

    protected List<String> logFiles;
    protected Pattern errorPattern;
    protected Pattern warningPattern;
    protected int sizeLimit;

    @Activate
    @Modified
    protected void activate(Config config) {
        super.activate(config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        slingHomePath = slingSettingsService.getSlingHomePath();
        slingHomeName = StringUtils.substringAfterLast(slingHomePath, "/");
        logFiles = Arrays.asList(Optional.ofNullable(config.logFiles()).orElse(new String[0]));
        errorPattern = Pattern.compile(config.errorPattern());
        warningPattern = Pattern.compile(config.warningPattern());
        sizeLimit = config.sizeLimit();
    }

    @Override
    public @NotNull String getLabel() {
        return StringUtils.defaultString(label, getName());
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode)
            throws IOException {
        if (OPTION_PAGE.equals(mode) || OPTION_VIEW.equals(mode)) {
            writer.append("<script>\n");
            copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/logfile/script.js", writer);
            writer.append("</script>\n");
        }
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final PrintWriter writer = response.getWriter();
        final String mode = getHtmlMode(request, HTML_MODES);
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        String logfile = pathInfo.getSuffix();
        if (StringUtils.isNotBlank(logfile) && !logFiles.contains(logfile)) {
            logfile = null;
        }
        final LoggerSession session = !OPTION_TILE.equals(mode) && StringUtils.isNotBlank(logfile)
                ? getLoggerSession(request, logfile, OPTION_PAGE.equals(mode) || resetTriggered(request))
                : null;
        switch (mode) {
            case OPTION_TILE:
                htmlTile(request, response, writer);
                return;
            case OPTION_VIEW:
            default:
                htmlView(request, response, session, writer);
                return;
            case OPTION_TAIL:
                if (session != null) {
                    response.setContentType("text/plain;charset=UTF-8");
                    htmlTail(request, response, session, writer);
                    return;
                }
                break;
            case OPTION_PAGE:
                response.setContentType("text/html;charset=UTF-8");
                htmlPageHead(writer);
                htmlView(request, response, session, writer);
                htmlPageTail(writer);
                return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    protected boolean resetTriggered(@NotNull final SlingHttpServletRequest request) {
        return StringUtils.defaultString(request.getHeader("Cache-Control"), "").contains("no");
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
                .append(" text-white\">").append("Logfiles")
                .append("</div><ul class=\"list-group list-group-flush\">\n");
        boolean reset = resetTriggered(request);
        for (final String logfile : logFiles) {
            final LoggerSession session = getLoggerSession(request, logfile, reset);
            if (session != null) {
                session.summarize();
                writer.append("<li class=\"list-group-item d-flex justify-content-between\">")
                        .append(session.getLogfile()).append("<span>");
                htmlBadges(writer, session);
                writer.append("</span></li>");
            }
        }
        writer.append("</ul></div>\n");
    }

    protected void htmlBadges(@NotNull final PrintWriter writer, @NotNull final LoggerSession session) {
        writer.append("<span class=\"dashboard-widget__badges\">");
        htmlBadge(writer, "danger", session.getErrors());
        htmlBadge(writer, "warning", session.getWarnings());
        htmlBadge(writer, "secondary", session.getLines());
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
                            @Nullable LoggerSession session, @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/logfile/style.css", writer);
        writer.append("</style>\n");
        if (session != null) {
            session.summarize();
            logfileView(request, response, session, writer);
        } else {
            if (logFiles.size() > 1) {
                writer.append("<div class=\"dashboard-widget__logfile-view logfile-tabs\" data-tabs-id=\"logfiles\">\n"
                        + "<ul class=\"resuming-tabs_nav nav nav-tabs\" id=\"myTab\" role=\"tablist\">\n");
                for (int i = 0; i < logFiles.size(); i++) {
                    session = getLoggerSession(request, logFiles.get(i), false);
                    if (session != null) {
                        session.summarize();
                        final String tabId = xssapi.encodeForHTMLAttr(tabId(session));
                        writer.append("<li class=\"resuming-tabs_nav-item nav-item\"><a class=\"nav-link")
                                .append(i == 0 ? " active" : "").append("\" id=\"tab").append(tabId)
                                .append("\" data-toggle=\"tab\" href=\"#panel").append(tabId)
                                .append("\" role=\"tab\" aria-controls=\"panel").append(tabId)
                                .append("\" aria-selected=\"false\">").append(xssapi.encodeForHTML(session.getLogfile()));
                        htmlBadges(writer, session);
                        writer.append("</a></li>\n");
                    }
                }
                writer.append("</ul>\n");
            }
            if (logFiles.size() > 1) {
                writer.append("<div class=\"resuming-tabs_content tab-content\">\n");
            }
            for (int i = 0; i < logFiles.size(); i++) {
                session = getLoggerSession(request, logFiles.get(i), false);
                if (session != null) {
                    if (logFiles.size() > 1) {
                        final String tabId = xssapi.encodeForHTMLAttr(tabId(session));
                        writer.append("<div class=\"resuming-tabs_pane tab-pane fade")
                                .append(i == 0 ? " show active" : "").append("\" id=\"panel").append(tabId)
                                .append("\" role=\"tabpanel\" aria-labelledby=\"tab").append(tabId)
                                .append("\">\n");
                    }
                    logfileView(request, response, session, writer);
                    if (logFiles.size() > 1) {
                        writer.append("</div>\n");
                    }
                }
            }
            if (logFiles.size() > 1) {
                writer.append("</div></div>\n");
            }
        }
    }

    protected String tabId(@NotNull final LoggerSession session) {
        return session.getLogfile()
                .replace('/', '-')
                .replace('.', '_');
    }

    protected void logfileView(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response,
                               @NotNull LoggerSession session, @NotNull final PrintWriter writer) {
        writer.append("<div class=\"dashboard-widget__logfile\"><textarea readonly=\"readonly\" data-tail=\"")
                .append(getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_TAIL))
                .append(session.getLogfile()).append("\">");
        session.dump(writer, true);
        writer.append("</textarea></div>\n");
    }

    protected void htmlTail(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final LoggerSession session, @NotNull final PrintWriter writer) {
        session.dump(writer, false);
    }

    protected @Nullable LoggerSession getLoggerSession(@NotNull final SlingHttpServletRequest request,
                                                       @Nullable final String path, boolean reset) {
        LoggerSession loggerSession = null;
        final HttpSession httpSession;
        if (StringUtils.isNotBlank(path) && (httpSession = request.getSession(true)) != null) {
            Map<String, LoggerSession> sessionSet = null;
            try {
                //noinspection unchecked
                sessionSet = (Map<String, LoggerSession>) httpSession.getAttribute(SA_SESSIONS);
            } catch (ClassCastException ignore) {
            }
            if (sessionSet == null) {
                httpSession.setAttribute(SA_SESSIONS, sessionSet = new HashMap<>());
            }
            if (!reset) {
                try {
                    loggerSession = sessionSet.get(path);
                } catch (ClassCastException ignore) {
                    httpSession.setAttribute(SA_SESSIONS, sessionSet = new HashMap<>());
                }
            }
            if (loggerSession == null) {
                final File file = new File("./" + slingHomeName + path);
                if (file.isFile() && file.canRead()) {
                    loggerSession = new LoggerSession(path, file, errorPattern, warningPattern, sizeLimit);
                    sessionSet.put(path, loggerSession);
                }
            }
        }
        return loggerSession;
    }
}