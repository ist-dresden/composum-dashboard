package com.composum.sling.dashboard.servlet.impl;

import com.composum.sling.dashboard.servlet.AbstractWidgetServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.settings.SlingSettingsService;
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
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.impl.DashboardLogfileWidget.RESOURCE_TYPE;

/**
 * a primitive logfile viewer servlet implementation to declare a Composum Dashborad Widget for logfiles
 */
@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Dashboard Logfile Widget",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE,
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/view",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/tail",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/tile",
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=" + RESOURCE_TYPE + "/page",
                ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=html"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = DashboardLogfileWidget.Config.class)
public class DashboardLogfileWidget extends AbstractWidgetServlet {

    public static final String RESOURCE_TYPE = "composum/dashboard/sling/components/logfile";

    @ObjectClassDefinition(name = "Composum Dashboard Logfile Widget")
    public @interface Config {

        @AttributeDefinition(name = "Logfile Set")
        String[] logFiles() default {
                "/logs/error.log"
        };

        @AttributeDefinition(name = "Error Pattern")
        String errorPattern() default "\\*ERROR\\*";

        @AttributeDefinition(name = "Warning Pattern")
        String warningPattern() default "\\*WARN\\*";

        @AttributeDefinition(name = "Size Limit (Kb)")
        int sizeLimit() default 1000;
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
    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_VIEW, OPTION_TAIL, OPTION_TILE, OPTION_PAGE);

    public static final String SA_SESSIONS = DashboardLogfileWidget.class.getName() + "#sessions";

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
    protected void activate(DashboardLogfileWidget.Config config) {
        slingHomePath = slingSettingsService.getSlingHomePath();
        slingHomeName = StringUtils.substringAfterLast(slingHomePath, "/");
        logFiles = Arrays.asList(config.logFiles());
        errorPattern = Pattern.compile(config.errorPattern());
        warningPattern = Pattern.compile(config.warningPattern());
        sizeLimit = config.sizeLimit();
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
                htmlPageHead(writer, "Logfiles");
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
                        .append(session.getLogfile()).append("<span>")
                        .append("&nbsp;<i class=\"badge badge-pill badge-danger\">")
                        .append(String.valueOf(session.getErrors())).append("</i>")
                        .append("&nbsp;<i class=\"badge badge-pill badge-warning\">")
                        .append(String.valueOf(session.getWarnings())).append("</i>")
                        .append("&nbsp;<i class=\"badge badge-pill badge-secondary\">")
                        .append(String.valueOf(session.getLines())).append("</i>")
                        .append("</span></li>");
            }
        }
        writer.append("</ul></div>\n");
    }

    protected void htmlView(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @Nullable LoggerSession session, @NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/logfile/style.css", writer);
        writer.append("</style>\n");
        if (session != null) {
            logfileView(request, response, session, writer);
        } else {
            if (logFiles.size() > 1) {
                writer.append("<div class=\"dasboard-widget__logfile-view\">\n"
                        + "<ul class=\"nav nav-tabs\" id=\"myTab\" role=\"tablist\">\n");
                for (int i = 0; i < logFiles.size(); i++) {
                    session = getLoggerSession(request, logFiles.get(i), false);
                    if (session != null) {
                        final String tabId = xssapi.encodeForHTMLAttr(tabId(session));
                        writer.append("<li class=\"nav-item\"><a class=\"nav-link").append(i == 0 ? " active" : "")
                                .append("\" id=\"tab").append(tabId)
                                .append("\" data-toggle=\"tab\" href=\"#panel").append(tabId)
                                .append("\" role=\"tab\" aria-controls=\"panel").append(tabId)
                                .append("\" aria-selected=\"false\">").append(xssapi.encodeForHTML(session.getLogfile()))
                                .append("</a></li>\n");
                    }
                }
                writer.append("</ul>\n");
            }
            if (logFiles.size() > 1) {
                writer.append("<div class=\"tab-content\">\n");
            }
            for (int i = 0; i < logFiles.size(); i++) {
                session = getLoggerSession(request, logFiles.get(i), false);
                if (session != null) {
                    if (logFiles.size() > 1) {
                        final String tabId = xssapi.encodeForHTMLAttr(tabId(session));
                        writer.append("<div class=\"tab-pane fade").append(i == 0 ? " show active" : "")
                                .append("\" id=\"panel").append(tabId)
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
        writer.append("<script>\n");
        copyResource(this.getClass(), "/com/composum/sling/dashboard/plugin/logfile/script.js", writer);
        writer.append("</script>\n");
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
                .append(getWidgetUri(request, RESOURCE_TYPE, HTML_MODES, OPTION_TAIL))
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