package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.SyntheticResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class AbstractWidgetServlet extends AbstractDashboardServlet implements DashboardWidget {

    protected static final String OPTION_TILE = "tile";
    protected static final String OPTION_VIEW = "view";
    protected static final String OPTION_PAGE = "page";

    public static final String TEMPLATE_BASE = "/com/composum/sling/dashboard/";
    public static final String PLUGIN_BASE = TEMPLATE_BASE + "plugin/";

    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_TILE);

    protected String name;
    protected List<String> context;
    protected List<String> category;
    protected int rank;
    protected String label;
    protected String navTitle;

    protected void activate(String name, String[] context, String[] category, int rank, String label, String navTitle,
                            String[] resourceTypes, String[] servletPaths) {
        this.name = name;
        this.context = Arrays.asList(context);
        this.category = Arrays.asList(category);
        this.rank = rank;
        this.label = label;
        this.navTitle = navTitle;
        super.activate(resourceTypes, servletPaths);
    }

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
        return context;
    }

    @Override
    public @NotNull Collection<String> getCategory() {
        return category;
    }

    @Override
    public int getRank() {
        return rank;
    }

    @Override
    public @NotNull String getName() {
        return StringUtils.isNotBlank(name) ? name : StringUtils.substringAfterLast(defaultResourceType(), "/");
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
    public <T> @NotNull T getProperty(@NotNull String name, @NotNull T defaultValue) {
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
        return getWidgetUri(getWidgetResource(request, resourceType), options, mode, selectors);
    }

    protected @NotNull String getWidgetUri(@Nullable final Resource widget, @NotNull final List<String> options,
                                           @Nullable final String mode, String... selectors) {
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
        return path.endsWith("/" + JCR_CONTENT)
                ? StringUtils.substringBeforeLast(path, "/" + JCR_CONTENT)
                : path;
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

    protected void htmlPageHead(@NotNull final PrintWriter writer, String... styles)
            throws IOException {
        final Set<String> styleSet = new LinkedHashSet<>();
        styleSet.add(TEMPLATE_BASE + "commons/style.css");
        styleSet.addAll(Arrays.asList(styles));
        final Map<String, Object> properties = new HashMap<>();
        properties.put("widget-type", getName());
        properties.put("title", getLabel());
        copyResource(getClass(), PLUGIN_BASE + "page/head.html", writer, properties);
        embedSnippets(writer, "style", styleSet);
        copyResource(getClass(), PLUGIN_BASE + "page/body-head.html", writer, properties);
        writer.append("</nav><div class=\"composum-dashboard__widget-view\">\n");
    }

    protected void htmlPageTail(@NotNull final PrintWriter writer, String... scripts)
            throws IOException {
        final Set<String> scriptSet = new LinkedHashSet<>();
        scriptSet.add(TEMPLATE_BASE + "commons/script.js");
        scriptSet.addAll(Arrays.asList(scripts));
        writer.append("</div>\n");
        copyResource(getClass(), PLUGIN_BASE + "page/script.html", writer, Collections.emptyMap());
        embedSnippets(writer, "script", scriptSet);
        embedScript(writer, OPTION_PAGE);
        copyResource(getClass(), PLUGIN_BASE + "page/tail.html", writer, Collections.emptyMap());
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
}

