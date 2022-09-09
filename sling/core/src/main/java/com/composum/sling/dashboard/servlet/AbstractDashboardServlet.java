package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.util.ValueEmbeddingReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class AbstractDashboardServlet extends SlingSafeMethodsServlet {

    public static final String JCR_CONTENT = "jcr:content";
    public static final String JCR_DATA = "jcr:data";
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String JCR_MIXIN_TYPES = "jcr:mixinTypes";
    public static final String NT_UNSTRUCTURED = "nt:unstructured";
    public static final String NT_RESOURCE = "nt:resource";
    public static final String NT_FILE = "nt:file";

    public static final String JSON_DATE_FORMAT = "yyyy-MM-dd MM:mm:ss.SSSZ";

    protected String resourceType;
    protected List<String> resourceTypes;
    protected String servletPath;
    protected List<String> servletPaths;

    protected void activate(String[] resourceTypes, String[] servletPaths) {
        this.resourceType = getFirstProperty(resourceTypes, defaultResourceType());
        this.resourceTypes = resourceTypes != null ? Arrays.asList(resourceTypes) : Collections.emptyList();
        this.servletPath = getFirstProperty(servletPaths, null);
        this.servletPaths = servletPaths != null ? Arrays.asList(servletPaths) : Collections.emptyList();
    }

    protected abstract @NotNull String defaultResourceType();

    public  @NotNull String getPagePath(@NotNull final SlingHttpServletRequest request) {
        return StringUtils.substringBefore(request.getResource().getPath(), "/jcr:content");
    }

    protected void copyResource(@NotNull final Class<?> context, @NotNull final String resourcePath,
                                @NotNull final Writer writer, @NotNull final Map<String, Object> properties)
            throws IOException {
        try (Reader reader = openResource(context, resourcePath, properties)) {
            if (reader != null) {
                IOUtils.copy(reader, writer);
            }
        }
    }

    protected void copyResource(@NotNull final Class<?> context, @NotNull final String resourcePath,
                                @NotNull final Writer writer)
            throws IOException {
        try (Reader reader = openResource(context, resourcePath)) {
            if (reader != null) {
                IOUtils.copy(reader, writer);
            }
        }
    }

    protected @Nullable Reader openResource(@NotNull final Class<?> context, @NotNull final String resourcePath,
                                            @NotNull final Map<String, Object> properties) {
        final Reader reader = openResource(context, resourcePath);
        return reader != null ? new ValueEmbeddingReader(reader, properties, null, context) : null;
    }

    protected @Nullable Reader openResource(@NotNull final Class<?> context, @NotNull final String resourcePath) {
        final InputStream stream = context.getClassLoader().getResourceAsStream(resourcePath);
        return stream != null ? new InputStreamReader(stream, StandardCharsets.UTF_8) : null;
    }

    protected void embedSnippets(@NotNull final PrintWriter writer, String type, Iterable<String> snippets) {
        for (String snippet : snippets) {
            if (snippet.startsWith("/")) {
                writer.append("<").append(type).append(">\n");
                try (final InputStream stream = getClass().getClassLoader().getResourceAsStream(snippet);
                     final Reader reader = stream != null ? new InputStreamReader(stream) : null) {
                    if (reader != null) {
                        IOUtils.copy(reader, writer);
                    }
                } catch (IOException ignore) {
                }
                writer.append("</").append(type).append(">\n");
            } else {
                writer.append(snippet);
            }
        }
    }

    public void loadPage(@NotNull final HttpServletResponse response, @NotNull final String template,
                         @NotNull final Map<String, Object> properties)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader().getResourceAsStream(template);
             final Reader reader = pageContent != null ? new ValueEmbeddingReader(
                     new InputStreamReader(pageContent), properties, Locale.ENGLISH, this.getClass()) : null) {
            if (reader != null) {
                response.setContentType("text/html;charset=UTF-8");
                IOUtils.copy(reader, response.getWriter());
                return;
            }
        } catch (Exception ignore) {
        }
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public static String getFirstProperty(@Nullable final String[] stringSet, final String defaultValue) {
        return stringSet != null && stringSet.length > 0 ? stringSet[0] : defaultValue;
    }

    public static List<Pattern> patternList(@Nullable final String[] config) {
        List<Pattern> patterns = new ArrayList<>();
        for (String rule : config) {
            if (StringUtils.isNotBlank(rule)) {
                patterns.add(Pattern.compile(rule));
            }
        }
        return patterns;
    }
}

