package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.util.ValueEmbeddingReader;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
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

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDashboardServlet.class);

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

    // generic content creation to create dashboard content elements

    /**
     * the generator implementation to generate a content element that represents the servlet itself
     *
     * @param request          the incommong request for content creation
     * @param response         the outgoing response for content creation
     * @param dashboardManager the dashboard manager instace to execute the content creation
     * @param contentGenerator the content generator implementation to use
     * @return 'true' if the content creation was successful
     */
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
            } catch (IOException e) {
                LOG.trace("createContent", e);
            }
        }
        return false;
    }

    /**
     * the content creator function to crteate a single resource
     *
     * @param parent          the parent resource of the resource to create
     * @param name            the repository name of the resource to create
     * @param primaryType     the primary node type of the new resource
     * @param label           an optional label (title) of the new resource
     * @param resourceType    the optional Sling resource type
     * @param propsCollection a optional custom properties stream of key value pairs
     * @return the created resource
     */
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
        collectHtmlCssClasses(cssClasses);
        return StringUtils.join(cssClasses, " ");
    }

    protected void collectHtmlCssClasses(@NotNull final Set<String> cssClasses) {
    }

    protected @NotNull String getRequestParameters(@NotNull final SlingHttpServletRequest request, boolean dropEmptyValues) {
        final StringBuilder parameters = new StringBuilder();
        for (final Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            final String name = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            for (final String value : entry.getValue()) {
                if (!dropEmptyValues || StringUtils.isNotBlank(value)) {
                    parameters.append(parameters.length() == 0 ? '?' : '&')
                            .append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            }
        }
        return parameters.toString();
    }

    protected int getIntParameter(@NotNull final SlingHttpServletRequest request,
                                  @NotNull final String name, int defaultValue) {
        final String value = request.getParameter(name);
        if (StringUtils.isNotBlank(value))
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignore) {
                LOG.trace("getIntParameter: value {} for {} {}", value, name, ignore.toString());
            }
        return defaultValue;
    }

    protected boolean getBooleanParameter(@NotNull final SlingHttpServletRequest request,
                                          @NotNull final String name, boolean defaultValue) {
        final String value = request.getParameter(name);
        return value != null ? List.of("true", "on", "").contains(value.toLowerCase()) : defaultValue;
    }

    //
    // generic HTML page / element rendering...
    //

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

    protected void sendFormFields(@NotNull final SlingHttpServletResponse response, List<String> formFields)
            throws IOException {
        prepareTextResponse(response, null);
        final PrintWriter writer = response.getWriter();
        for (final String field : formFields) {
            final Map<String, Object> properties = new HashMap<>();
            for (final String term : StringUtils.split(field, ',')) {
                final String[] keyValue = StringUtils.split(term, "=", 2);
                final String value = keyValue.length > 1 ? keyValue[1] : "";
                if (value.contains("|")) {
                    final StringBuilder options = new StringBuilder();
                    for (final String option : StringUtils.split(value, '|')) {
                        final String[] valLabel = StringUtils.splitPreserveAllTokens(option, ":", 2);
                        options.append("<option value=\"").append(valLabel[0]).append("\">")
                                .append(valLabel.length > 1 ? valLabel[1] : valLabel[0]).append("</option>");
                    }
                    properties.put(keyValue[0], options.toString());
                } else {
                    properties.put(keyValue[0], value);
                }
            }
            embedTemplate(writer, "form/" + properties.get("type") + ".html", properties);
        }
    }

    protected void embedTemplate(@NotNull final Writer responseWriter,
                                 @NotNull final String scriptResource,
                                 @NotNull final Map<String, Object> properties)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/" + scriptResource);
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                final Writer writer = new ValueEmbeddingWriter(responseWriter, properties,
                        Locale.ENGLISH, this.getClass());
                IOUtils.copy(reader, writer);
            }
        }
    }

    protected @Nullable String loadTemplate(@NotNull final String scriptResource,
                                            @NotNull final Map<String, Object> properties)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/" + scriptResource);
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                return IOUtils.toString(new ValueEmbeddingReader(reader, properties, Locale.ENGLISH, this.getClass()));
            }
        }
        return null;
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
                } catch (IOException e) {
                    LOG.trace("embedSnippets: for {} {}", snippet, e.toString());
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
        } catch (Exception e) {
            LOG.trace("loadPage: for {} {}", template, e.toString());
        }
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
