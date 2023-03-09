package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.util.ValueEmbeddingReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.composum.sling.dashboard.DashboardConfig.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.JCR_TITLE;
import static com.composum.sling.dashboard.DashboardConfig.SLING_RESOURCE_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.getFirstProperty;

public abstract class AbstractDashboardServlet extends SlingSafeMethodsServlet {

    public static final String SELECTOR_CREATE_CONTENT = "create.content";

    protected String resourceType;
    protected List<String> resourceTypes;
    protected String servletPath;
    protected List<String> servletPaths;

    protected BundleContext bundleContext;

    protected void activate(@Nullable final BundleContext bundleContext,
                            @Nullable final String[] resourceTypes, @Nullable final String[] servletPaths) {
        this.bundleContext = bundleContext;
        this.resourceType = getFirstProperty(resourceTypes, defaultResourceType());
        this.resourceTypes = resourceTypes != null ? Arrays.asList(resourceTypes) : Collections.emptyList();
        this.servletPath = getFirstProperty(servletPaths, null);
        this.servletPaths = servletPaths != null ? Arrays.asList(servletPaths) : Collections.emptyList();
    }

    protected abstract @NotNull String defaultResourceType();

    public @NotNull String getPagePath(@NotNull final SlingHttpServletRequest request) {
        return StringUtils.substringBefore(request.getResource().getPath(), "/jcr:content");
    }

    protected boolean createContent(@NotNull final SlingHttpServletRequest request,
                                    @NotNull final SlingHttpServletResponse response,
                                    @NotNull final DashboardManager dashboardManager,
                                    @NotNull final ContentGenerator contentGenerator) {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        if (SELECTOR_CREATE_CONTENT.equals(pathInfo.getSelectorString())) {
            try {
                final String path = pathInfo.getSuffix();
                if (StringUtils.isNotBlank(path) &&
                        dashboardManager.createContentPage(request, response, path, contentGenerator)) {
                    request.getResourceResolver().commit();
                    response.sendRedirect(request.getContextPath() + path + ".html");
                    return true;
                }
            } catch (IOException ignore) {
            }
        }
        return false;
    }

    protected @Nullable Resource createContent(@NotNull final Resource parent,
                                               @NotNull final String name, @NotNull String primaryType,
                                               @Nullable final String label, @Nullable final String resourceType,
                                               Object... propsCollection)
            throws PersistenceException {
        Resource resource = null;
        final ResourceResolver resolver = parent.getResourceResolver();
        final Map<String, Object> properties = new HashMap<>();
        properties.put(JCR_PRIMARY_TYPE, primaryType);
        if (StringUtils.isNotBlank(resourceType)) {
            properties.put(SLING_RESOURCE_TYPE, resourceType);
        }
        if (StringUtils.isNotBlank(label)) {
            properties.put(JCR_TITLE, label);
        }
        String key = null;
        for (Object arg : propsCollection) {
            if (key == null) {
                key = arg.toString();
            } else {
                properties.put(key, arg);
                key = null;
            }
        }
        if (StringUtils.isNotBlank((String) properties.get(JCR_PRIMARY_TYPE))) {
            resource = resolver.create(parent, name, properties);
        }
        return resource;
    }

    protected @NotNull String getHtmlCssClasses(@NotNull final String mainHtmlClass) {
        final Set<String> cssClasses = new TreeSet<>();
        cssClasses.add(mainHtmlClass);
        if (bundleContext != null) {
            Optional.ofNullable(bundleContext.getServiceReference(DashboardManager.class))
                    .map(serviceReference -> bundleContext.getService(serviceReference))
                    .ifPresent(dashboardManager -> dashboardManager.addRunmodeCssClasses(cssClasses));
        }
        return StringUtils.join(cssClasses, " ");
    }

    @Deprecated(since = "1.1.2 - use prepareTextResponse()")
    protected void prepareHtmlResponse(@NotNull final HttpServletResponse response) {
        prepareTextResponse(response, null);
    }

    protected void prepareTextResponse(@NotNull final HttpServletResponse response, @Nullable String contentType) {
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        contentType = StringUtils.defaultString(contentType, "text/html");
        if (!contentType.contains("charset")) {
            contentType += ";charset=UTF-8";
        }
        response.setContentType(contentType);
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
                prepareTextResponse(response, null);
                IOUtils.copy(reader, response.getWriter());
                return;
            }
        } catch (Exception ignore) {
        }
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}

