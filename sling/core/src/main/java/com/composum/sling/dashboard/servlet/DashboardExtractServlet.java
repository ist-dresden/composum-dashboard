package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ResourceExtractConfig;
import com.composum.sling.dashboard.service.ResourceExtractService;
import com.composum.sling.dashboard.service.ResourceExtractService.ExtractSession;
import com.composum.sling.dashboard.service.ResourceExtractService.Extractor;
import com.composum.sling.dashboard.service.ResourceExtractService.Mode;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Component(service = {Servlet.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardExtractServlet.Config.class)
public class DashboardExtractServlet extends SlingAllMethodsServlet {

    @ObjectClassDefinition(
            name = "Composum Dashboard Extract Service configuration"
    )
    @interface Config {

        @AttributeDefinition(
                name = "Predefined Paths",
                description = "the set of resource paths to extract in the 'predefined' mode"
        )
        String[] predefinedPaths();

        @AttributeDefinition(
                name = "Path Rule Set",
                description = "source to target mapping rules; e.g. '/content/dam(/[^/]+(/.*)),dam:Asset,1,/content/dam/test$2'"
        )
        String[] pathRuleSet();

        @AttributeDefinition(
                name = "add. ZIP Entries",
                description = "entry patterns to add to ZIP; e.g. 'dam:Asset,jcr:content/renditions/original'"
        )
        String[] addZipEntries();

        @AttributeDefinition(
                name = "Ignored Children",
                description = "path patterns for resources children to ignore; e.g. '^.+/jcr:content/renditions/cq5dam\\..+$'"
        )
        String[] ignoredChildren();

        @AttributeDefinition(
                name = "Ignored Properties",
                description = "name patterns of properties to ignore; e.g. '^jcr:(created|lastModified).*$'"
        )
        String[] ignoredProperties() default {
                "^jcr:(uuid)$",
                "^jcr:(baseVersion|predecessors|versionHistory|isCheckedOut)$",
                "^jcr:(created|lastModified).*$",
                "^cq:last(Modified|Replicat|Rolledout).*$"
        };

        @AttributeDefinition(name = "Servlet Methods",
                description = "the HTTP methods supported by this servlet")
        String[] sling_servlet_methods() default {
                HttpConstants.METHOD_GET
        };

        @AttributeDefinition(name = "Servlet Selectors",
                description = "the Sling selectors supported by this servlet")
        String[] sling_servlet_selectors() default {
                "extract.paths",
                "extract.scan",
                "extract.copy",
                "extract.src"
        };

        @AttributeDefinition(name = "Resource Types",
                description = "the Sling resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                ServletResolverConstants.DEFAULT_RESOURCE_TYPE
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible URL extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "txt",
                "json",
                "zip"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servlet paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    private static final Logger LOG = LoggerFactory.getLogger(DashboardExtractServlet.class);

    @Reference
    private ResourceExtractService extractService;

    protected ResourceExtractConfig config;

    @Activate
    @Modified
    protected void activate(final Config config) {
        this.config = new ResourceExtractConfig() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return config.annotationType();
            }

            @Override
            public String[] predefinedPaths() {
                return config.predefinedPaths();
            }

            @Override
            public String[] pathRuleSet() {
                return config.pathRuleSet();
            }

            @Override
            public String[] addZipEntries() {
                return config.addZipEntries();
            }

            @Override
            public String[] ignoredChildren() {
                return config.ignoredChildren();
            }

            @Override
            public String[] ignoredProperties() {
                return config.ignoredProperties();
            }
        };
    }

    @Override
    protected void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        doIt(request, response, request.getParameterValues("path"));
    }

    @Override
    protected void doPost(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        doIt(request, response, request.getParameterValues("path"));
    }

    @Override
    protected void doPut(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        doIt(request, response, IOUtils.readLines(request.getInputStream(), StandardCharsets.UTF_8).toArray(new String[0]));
    }

    protected void doIt(@NotNull final SlingHttpServletRequest slingRequest,
                        @NotNull final SlingHttpServletResponse response,
                        final String... paths) throws IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            final RequestPathInfo pathInfo = request.getRequestPathInfo();
            final String[] selectors = pathInfo.getSelectors();
            if (selectors.length > 1) {
                final boolean dryRun = Boolean.parseBoolean(Optional.ofNullable(request.getParameter("dryRun")).orElse("true"));
                final Mode mode = Mode.valueOf(Optional.ofNullable(request.getParameter("mode")).orElse("merge").toUpperCase());
                final int levelMax = Integer.parseInt(Optional.ofNullable(request.getParameter("level")).orElse("4"));
                final ExtractSession session = extractService.createSession(config, request.getResourceResolver(), dryRun, mode, levelMax);
                if (paths != null && paths.length > 0) {
                    session.scanExtractPaths(paths);
                } else {
                    session.scanExtractPaths(request.getResource());
                }
                switch (selectors[1]) {
                    case "paths": {
                        pathsResponse(response, Optional.ofNullable(pathInfo.getExtension()).orElse("txt"), session, "source");
                    }
                    return;
                    case "scan": {
                        pathsResponse(response, Optional.ofNullable(pathInfo.getExtension()).orElse("txt"), session);
                    }
                    return;
                    case "copy": {
                        try (final Extractor extractor = extractService.createCopyExtractor(config, session)) {
                            session.extract(extractor);
                        }
                        pathsResponse(response, Optional.ofNullable(pathInfo.getExtension()).orElse("txt"), session);
                    }
                    return;
                    case "src": {
                        response.setContentType("application/zip");
                        response.addHeader("Content-Disposition", "attachment; filename="
                                + request.getResource().getName() + "-extract.zip");
                        try (final Extractor extractor = extractService.createZipExtractor(config, session, response.getOutputStream())) {
                            session.extract(extractor);
                        }
                    }
                    return;
                }
            }
        } catch (Exception ex) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_GONE);
                response.setContentType("text/plain;charset=UTF-8");
                PrintWriter writer = response.getWriter();
                writer.println(ex.toString());
                ex.printStackTrace(writer);
            }
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    protected void pathsResponse(@NotNull final SlingHttpServletResponse response, @NotNull final String ext,
                                 @NotNull final ExtractSession session, final String... key) throws IOException {
        final PrintWriter writer = response.getWriter();
        switch (ext) {
            default:
            case "txt":
                prepareTextResponse(response, "text/plain");
                for (String name : key != null && key.length > 0 ? key : session.getPathSets().keySet().toArray(new String[0])) {
                    writePlainText(writer, name, session.getPathSets().get(name));
                }
                break;
            case "json":
                prepareTextResponse(response, "application/json");
                final JsonWriter jsonWriter = new JsonWriter(writer);
                jsonWriter.setIndent("  ");
                jsonWriter.beginObject();
                for (String name : key != null && key.length > 0 ? key : session.getPathSets().keySet().toArray(new String[0])) {
                    writeJson(jsonWriter, name, session.getPathSets().get(name));
                }
                jsonWriter.endObject();
                break;
        }
    }

    protected void writePlainText(@NotNull final PrintWriter writer, @Nullable final String name, @NotNull final Set<String> pathSet) {
        writer.println(name);
        for (final String path : pathSet) {
            writer.append("  ").println(path);
        }
    }

    protected void writeJson(@NotNull final JsonWriter writer, @Nullable final String name, @NotNull final Set<String> pathSet)
            throws IOException {
        writer.name(name).beginArray();
        for (final String path : pathSet) {
            writer.value(path);
        }
        writer.endArray();
    }

    protected void prepareTextResponse(@NotNull final HttpServletResponse response, @Nullable String contentType) {
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        contentType = StringUtils.defaultString(contentType, "text/plain");
        if (!contentType.contains("charset")) {
            contentType += ";charset=UTF-8";
        }
        response.setContentType(contentType);
    }
}
