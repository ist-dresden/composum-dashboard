package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.util.ValueEmbeddingReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public abstract class AbstractWidgetServlet extends SlingSafeMethodsServlet {

    protected static final String OPTION_TILE = "tile";
    protected static final String OPTION_VIEW = "view";
    protected static final String OPTION_PAGE = "page";

    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_VIEW, OPTION_TILE, OPTION_PAGE);

    protected List<Pattern> allowedPathPatterns;
    protected List<Pattern> disabledPathPatterns;

    protected @NotNull String getHtmlMode(@NotNull final SlingHttpServletRequest request,
                                          @NotNull final List<String> options) {
        final String selectorMode = getSelectorMode(request, options);
        if (StringUtils.isNotBlank(selectorMode)) {
            return selectorMode;
        }
        final String resourceType = request.getResource().getResourceType();
        if (StringUtils.isNotBlank(resourceType)) {
            for (String option : options) {
                if (resourceType.endsWith("/" + option)) {
                    return option;
                }
            }
        }
        return options.get(0);
    }

    protected String getWidgetUri(@NotNull final SlingHttpServletRequest request, @NotNull final String resourceType,
                                  @NotNull final List<String> options, @Nullable final String mode) {
        final Resource widget = getWidgetResource(request, resourceType);
        final StringBuilder uri = new StringBuilder(widget.getPath());
        uri.append(StringUtils.isNotBlank(getSelectorMode(request, options)) ? '.' : '/');
        if (StringUtils.isNotBlank(mode)) {
            uri.append(mode).append(".html");
        }
        return uri.toString().replaceAll("/jcr:", "/_jcr_");
    }

    protected @Nullable String getSelectorMode(@NotNull final SlingHttpServletRequest request,
                                               @NotNull final List<String> options) {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final String[] selectors = pathInfo.getSelectors();
        for (String selector : selectors) {
            if (options.contains(selector)) {
                return selector;
            }
        }
        return null;
    }

    protected Resource getWidgetResource(@NotNull final SlingHttpServletRequest request,
                                         @NotNull final String resourceType) {
        Resource widget = request.getResource();
        if (!widget.isResourceType(resourceType)) {
            widget = Objects.requireNonNull(widget.getParent());
        }
        return widget;
    }

    protected @Nullable Resource resolveUrl(@NotNull final SlingHttpServletRequest request,
                                            @Nullable final String urlString) {
        Resource result = null;
        if (StringUtils.isNotBlank(urlString)) {
            final ResourceResolver resolver = request.getResourceResolver();
            try {
                final URL url = new URL(urlString);
                String path = url.getPath();
                Resource target = resolver.resolve(request, path);
                if (ResourceUtil.isNonExistingResource(target)) {
                    String contextPath = request.getContextPath();
                    if (path.startsWith(contextPath)) {
                        path = path.substring(contextPath.length());
                        target = resolver.resolve(request, path);
                    }
                }
                if (!ResourceUtil.isNonExistingResource(target)) {
                    result = target;
                }
            } catch (MalformedURLException ignore) {
            }
        }
        return result;
    }

    protected List<Pattern> patternList(@Nullable final String[] config) {
        List<Pattern> patterns = new ArrayList<>();
        for (String rule : config) {
            if (StringUtils.isNotBlank(rule)) {
                patterns.add(Pattern.compile(rule));
            }
        }
        return patterns;
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

    protected void htmlPageHead(@NotNull final PrintWriter writer, @NotNull final String title) {
        writer.append("<html class=\"c-dashboard-s-widget__page\" lang=\"en\"><head>\n"
                        + "<meta charset=\"utf-8\">\n"
                        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                        + "<title>").append(title).append("</title>\n"
                        + "<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@4.1.3/dist/css/bootstrap.min.css\" integrity=\"sha384-MCw98/SFnGE8fJT3GXwEOngsV7Zt27NXFoaoApmYm81iuXoPkFOJwJ8ERdknLPMO\" crossorigin=\"anonymous\">\n"
                        + "</head><body class=\"c-dashboard-s-widget__page-body ")
                .append(title.replaceAll(" +", "-").toLowerCase())
                .append("\"><nav class=\"c-dashboard-s-widget__navbar navbar navbar-dark bg-dark\">\n"
                        + "<span class=\"navbar-brand\">")
                .append(title).append("</span>\n"
                        + "</nav><div class=\"c-dashboard-s-widget__view\">\n");
    }

    protected void htmlPageTail(@NotNull final PrintWriter writer) {
        writer.append("</div>\n"
                + "<script src=\"https://code.jquery.com/jquery-3.6.0.min.js\" integrity=\"sha256-/xUj+3OJU5yExlq6GSYGSHk7tPXikynS7ogEvDej/m4=\" crossorigin=\"anonymous\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/popper.js@1.14.3/dist/umd/popper.min.js\" integrity=\"sha384-ZMP7rVo3mIykV+2+9J3UJ46jBk0WLaUAdn689aCwoqbBJiSnjAK/l8WvCWPIPm49\" crossorigin=\"anonymous\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/bootstrap@4.1.3/dist/js/bootstrap.min.js\" integrity=\"sha384-ChfqqxuZUCnJSK3+MXmPNIyE6ZbWh2IMqE241rYiqJxyMiZ6OW/JmZQ5stwEULTy\" crossorigin=\"anonymous\"></script>\n"
                + "</body></html>\n");
    }
}

