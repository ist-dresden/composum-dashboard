package com.composum.sling.dashboard.service.impl;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardPlugin;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.servlet.AbstractWidgetServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component(
        service = {DashboardManager.class, ResourceFilter.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = SlingDashboardManager.Config.class)
public class SlingDashboardManager implements DashboardManager, ResourceFilter {

    @ObjectClassDefinition(name = "Composum Sling Dashboard Manager")
    public @interface Config {

        @AttributeDefinition(name = "Allowed Property Patterns")
        String[] allowedPropertyPatterns() default {
                "^.*$"
        };

        @AttributeDefinition(name = "Disabled Property Patterns")
        String[] disabledPropertyPatterns() default {
                "^rep:.*$",
                "^.*password.*$"
        };

        @AttributeDefinition(name = "Allowed Path Patterns")
        String[] allowedPathPatterns() default {
                "^/$",
                "^/content(/.*)?$",
                "^/conf(/.*)?$",
                "^/var(/.*)?$",
                "^/mnt(/.*)?$"
        };

        @AttributeDefinition(name = "Disabled Path Patterns")
        String[] disabledPathPatterns() default {
                ".*/rep:.*",
                "^(/.*)?/api(/.*)?$"
        };

        @AttributeDefinition(name = "Sortable Types")
        String[] sortableTypes() default {
                "nt:folder", "sling:Folder"
        };

        @AttributeDefinition(name = "Login URI")
        String loginUri() default "/system/sling/form/login.html";
    }

    protected static final String SA_WIDGETS = SlingDashboardManager.class.getName() + "#";

    protected List<Pattern> allowedPropertyPatterns;
    protected List<Pattern> disabledPropertyPatterns;
    protected List<Pattern> allowedPathPatterns;
    protected List<Pattern> disabledPathPatterns;
    protected List<String> sortableTypes;
    protected String loginUri;

    protected final List<DashboardPlugin> dashboardPlugins = new ArrayList<>();

    @Reference(
            service = DashboardPlugin.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE
    )
    protected void bindDashboardPlugin(@NotNull final DashboardPlugin plugin) {
        synchronized (dashboardPlugins) {
            dashboardPlugins.add(plugin);
            dashboardPlugins.sort(Comparator.comparingInt(DashboardPlugin::getRank));
        }
    }

    protected void unbindDashboardPlugin(@NotNull final DashboardPlugin plugin) {
        synchronized (dashboardPlugins) {
            dashboardPlugins.remove(plugin);
        }
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        allowedPropertyPatterns = AbstractWidgetServlet.patternList(config.allowedPropertyPatterns());
        disabledPropertyPatterns = AbstractWidgetServlet.patternList(config.disabledPropertyPatterns());
        allowedPathPatterns = AbstractWidgetServlet.patternList(config.allowedPathPatterns());
        disabledPathPatterns = AbstractWidgetServlet.patternList(config.disabledPathPatterns());
        sortableTypes = Arrays.asList(Optional.ofNullable(config.sortableTypes()).orElse(new String[0]));
        loginUri = config.loginUri();
    }

    @Override
    public boolean isAllowedProperty(@NotNull final String name) {
        for (Pattern allowed : allowedPropertyPatterns) {
            if (allowed.matcher(name).matches()) {
                for (Pattern disabled : disabledPropertyPatterns) {
                    if (disabled.matcher(name).matches()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAllowedResource(@NotNull final Resource resource) {
        final String path = resource.getPath();
        for (Pattern allowed : allowedPathPatterns) {
            if (allowed.matcher(path).matches()) {
                for (Pattern disabled : disabledPathPatterns) {
                    if (disabled.matcher(path).matches()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Resource getRequestResource(@NotNull final SlingHttpServletRequest request) {
        Resource resource = Optional.ofNullable(request.getRequestPathInfo().getSuffixResource())
                .orElse(request.getResourceResolver().getResource("/"));
        return resource != null && isAllowedResource(resource) ? resource : null;
    }

    @Override
    public boolean isSortableType(@Nullable final String type) {
        return StringUtils.isNotBlank(type) && sortableTypes.contains(type);
    }

    @Override
    public @NotNull String getLoginUri() {
        return loginUri;
    }

    @Override
    public @Nullable DashboardWidget getWidget(@NotNull final SlingHttpServletRequest request,
                                               @Nullable final String context, @NotNull final String name) {
        for (DashboardWidget widget : getWidgets(request, null)) {
            if (name.equals(widget.getName())) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public Collection<DashboardWidget> getWidgets(@NotNull final SlingHttpServletRequest request,
                                                  @Nullable final String context) {
        Map<String, DashboardWidget> wigetSet = new TreeMap<>();
        for (DashboardPlugin plugin : dashboardPlugins) {
            plugin.provideWidgets(request, context, wigetSet);
        }
        List<DashboardWidget> widgets = new ArrayList<>(wigetSet.values());
        widgets.sort(DashboardWidget.COMPARATOR);
        return widgets;
    }
}
