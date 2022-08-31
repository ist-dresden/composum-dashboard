package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.util.ValueEmbeddingReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.metatype.annotations.AttributeDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class AbstractWidgetServlet extends SlingSafeMethodsServlet implements DashboardWidget {

    public interface Config {

        @AttributeDefinition(name = "Context")
        String[] context();

        @AttributeDefinition(name = "Category")
        String category();

        @AttributeDefinition(name = "Rank")
        int rank();

        @AttributeDefinition(name = "Label")
        String label();

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Servlet Types")
        String[] sling_servlet_resourceTypes();

        @AttributeDefinition(name = "Servlet Extensions")
        String[] sling_servlet_extensions();

        @AttributeDefinition(name = "Servlet Paths")
        String[] sling_servlet_paths();
    }

    public static final String JSON_DATE_FORMAT = "yyyy-MM-dd MM:mm:ss.SSSZ";

    protected static final String OPTION_TILE = "tile";
    protected static final String OPTION_VIEW = "view";
    protected static final String OPTION_PAGE = "page";

    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_TILE);

    protected List<String> context;
    protected String category;
    protected int rank;
    protected String label;
    protected String navTitle;

    protected String resourceType;
    protected List<String> resourceTypes;
    protected String servletPath;
    protected List<String> servletPaths;

    protected void activate(String[] context, String categors, int rank, String label, String navTitle,
                            String[] resourceTypes, String[] servletPaths) {
        this.context = Arrays.asList(context);
        this.category = categors;
        this.rank = rank;
        this.label = label;
        this.navTitle = navTitle;
        this.resourceType = getFirstProperty(resourceTypes, defaultResourceType());
        this.resourceTypes = resourceTypes != null ? Arrays.asList(resourceTypes) : Collections.emptyList();
        this.servletPath = getFirstProperty(servletPaths, null);
        this.servletPaths = servletPaths != null ? Arrays.asList(servletPaths) : Collections.emptyList();
    }

    protected abstract @NotNull String defaultResourceType();

    // Widget

    @Override
    public boolean equals(Object other) {
        return other instanceof DashboardWidget && getName().equals(((DashboardWidget) other).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public @NotNull Collection<String> getContext() {
        return Collections.unmodifiableList(context);
    }

    @Override
    public @NotNull String getCategory() {
        return StringUtils.defaultString(category, getName());
    }

    @Override
    public int getRank() {
        return rank;
    }

    @Override
    public @NotNull String getName() {
        return StringUtils.substringAfterLast(defaultResourceType(), "/");
    }

    @Override
    public @NotNull String getLabel() {
        return StringUtils.defaultString(label, getName());
    }

    @Override
    public @NotNull String getNavTitle() {
        return StringUtils.defaultString(navTitle, getLabel());
    }

    @Override
    public @NotNull Resource getWidgetResource(@NotNull SlingHttpServletRequest request) {
        if (StringUtils.isNotBlank(servletPath)) {
            return new SyntheticResource(request.getResourceResolver(), servletPath, resourceType);
        } else {
            final Resource resource = request.getResource();
            return new ResourceWrapper(resource) {
                @Override
                public @NotNull String getResourceType() {
                    return resourceType;
                }
            };
        }
    }

    @Override
    public @NotNull String getWidgetPageUrl(@NotNull SlingHttpServletRequest request) {
        return (StringUtils.isNotBlank(servletPath) ? servletPath : request.getResource().getPath()) + ".html";
    }

    @Override
    public <T> @Nullable T getProperty(@NotNull String name, T defaultValue) {
        return defaultValue;
    }

    // Helpers

    protected @NotNull String getHtmlMode(@NotNull final SlingHttpServletRequest request,
                                          @NotNull final List<String> options) {
        final List<String> selectorMode = getSelectorMode(request, options);
        if (!selectorMode.isEmpty()) {
            return selectorMode.get(0);
        }
        String resourceType = request.getResource().getResourceType();
        if (StringUtils.isNotBlank(resourceType)) {
            resourceType = resourceType.replaceAll("\\.servlet$", "");
            for (String option : options) {
                if (resourceType.endsWith("/" + option)) {
                    return option;
                }
            }
        }
        final List<String> suffixMode = getSuffixMode(request, options);
        return suffixMode.size() > 0 ? suffixMode.get(0) : options.get(0);
    }

    protected @Nullable String getHtmlSubmode(@NotNull final SlingHttpServletRequest request,
                                              @NotNull final List<String> options) {
        final List<String> selectorMode = getSelectorMode(request, options);
        if (selectorMode.size() > 1) {
            return selectorMode.get(1);
        }
        final List<String> suffixMode = getSuffixMode(request, options);
        return suffixMode.size() > 1 ? suffixMode.get(1) : null;
    }

    protected @Nullable Resource getWidgetResource(@NotNull final SlingHttpServletRequest request,
                                                   @NotNull final String resourceType) {
        Resource widget = request.getResource();
        if (!widget.isResourceType(resourceType) && !resourceType.equals(widget.getResourceType())) {
            Resource parent = widget.getParent();
            if (parent != null && (parent.isResourceType(resourceType)
                    || resourceType.equals(parent.getResourceType()))) {
                return parent;
            }
            if (StringUtils.isNotBlank(servletPath)) {
                return new SyntheticResource(request.getResourceResolver(), servletPath, resourceType);
            }
            return null;
        }
        return widget;
    }

    protected @NotNull String getWidgetUri(@NotNull final SlingHttpServletRequest request,
                                           @NotNull final String resourceType, @NotNull final List<String> options,
                                           @Nullable final String mode, String... selectors) {
        final Resource widget = getWidgetResource(request, resourceType);
        if (widget != null) {
            String uri = getWidgetPath(widget, mode);
            if (StringUtils.isNotBlank(mode) && !uri.endsWith("/" + mode)) {
                uri += "." + mode;
                for (String sel : selectors) {
                    uri += "." + sel;
                }
            } else {
                for (String sel : selectors) {
                    uri += "/" + sel;
                }
            }
            uri += ".html";
            return uri.replaceAll("/jcr:", "/_jcr_");
        }
        return "";
    }

    protected @NotNull String getWidgetPath(@NotNull final Resource widget, @Nullable final String mode) {
        String path = widget.getPath();
        if (StringUtils.isNotBlank(mode)) {
            Resource modeResource = widget.getChild(mode);
            if (modeResource != null) {
                path = modeResource.getPath();
            } else if (servletPaths.contains(path + "/" + mode)) {
                path += "/" + mode;
            }
        }
        return path;
    }

    protected @NotNull List<String> getSelectorMode(@NotNull final SlingHttpServletRequest request,
                                                    @NotNull final List<String> options) {
        List<String> result = new ArrayList<>();
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final String[] selectors = pathInfo.getSelectors();
        for (int i = 0; i < selectors.length; i++) {
            if (options.contains(selectors[i])) {
                for (; i < selectors.length; i++) {
                    result.add(selectors[i]);
                }
            }
        }
        return result;
    }

    protected @NotNull List<String> getSuffixMode(@NotNull final SlingHttpServletRequest request,
                                                  @NotNull final List<String> options) {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final String suffix = Optional.ofNullable(pathInfo.getSuffix())
                .map(s -> s.startsWith("/") ? s.substring(1) : s)
                .orElse("");
        if (StringUtils.isNotBlank(suffix)) {
            List<String> keys = Arrays.asList(StringUtils.split(suffix, "/"));
            if (options.contains(keys.get(0))) {
                return keys;
            }
        }
        return Collections.emptyList();
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
                        + "<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@4.1.3/dist/css/bootstrap.min.css\""
                        + " integrity=\"sha384-MCw98/SFnGE8fJT3GXwEOngsV7Zt27NXFoaoApmYm81iuXoPkFOJwJ8ERdknLPMO\" crossorigin=\"anonymous\">\n"
                        + "<link href=\"https://stackpath.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css\" rel=\"stylesheet\""
                        + " integrity=\"sha384-wvfXpqpZZVQGK6TAh5PVlGOfQNHSoD2xbE+QkPxCAFlNEevoEH3Sl0sibVcOQVnN\" crossorigin=\"anonymous\">\n"
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

    protected @Nullable Object filterValues(@Nullable Object value, @NotNull final Collection<Pattern> blacklist) {
        if (value instanceof String[]) {
            List<String> values = new ArrayList<>();
            for (String string : (String[]) value) {
                boolean skip = false;
                for (Pattern pattern : blacklist) {
                    if (pattern.matcher(string).matches()) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    values.add(string);
                }
            }
            value = values.toArray(new String[0]);
        }
        return value;
    }

    protected void jsonProperty(@NotNull final JsonWriter writer, @Nullable final Object value)
            throws IOException {
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof Object[]) {
            writer.beginArray();
            for (Object item : (Object[]) value) {
                jsonProperty(writer, item);
            }
            writer.endArray();
        } else if (value instanceof Collection) {
            writer.beginArray();
            for (Object item : (Collection<?>) value) {
                jsonProperty(writer, item);
            }
            writer.endArray();
        } else if (value instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> item : ((Map<?, ?>) value).entrySet()) {
                writer.name(item.getKey().toString());
                jsonProperty(writer, item.getValue());
            }
            writer.endObject();
        } else if (value instanceof Date) {
            writer.value(new SimpleDateFormat(JSON_DATE_FORMAT).format((Date) value));
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else if (value instanceof Long) {
            writer.value((Long) value);
        } else if (value instanceof Integer) {
            writer.value((Integer) value);
        } else if (value instanceof Double) {
            writer.value((Double) value);
        } else if (value instanceof Number) {
            writer.value((Number) value);
        } else {
            writer.value(value.toString());
        }
    }

    protected String getFirstProperty(@Nullable final String[] stringSet, final String defaultValue) {
        return stringSet != null && stringSet.length > 0 ? stringSet[0] : defaultValue;
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
}

