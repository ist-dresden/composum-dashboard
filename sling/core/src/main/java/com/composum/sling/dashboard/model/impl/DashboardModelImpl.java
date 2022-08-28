package com.composum.sling.dashboard.model.impl;

import com.composum.sling.dashboard.model.DashboardModel;
import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Model(
        adaptables = SlingHttpServletRequest.class,
        adapters = DashboardModel.class,
        resourceType = "composum/dashboard/sling/components/page",
        cache = true
)
public class DashboardModelImpl implements DashboardModel {

    public class NavigationItem {

        protected final String label;
        protected final String title;
        protected final String linkUrl;
        protected final String linkPath;
        protected final String linkTarget;

        public NavigationItem(@NotNull final Resource resource) {
            final ValueMap values = resource.getValueMap();
            label = values.get("label", values.get("jcr:title", resource.getName()));
            title = values.get("title", values.get("jcr:title", ""));
            linkUrl = values.get("linkUrl", String.class);
            linkPath = values.get("linkPath", String.class);
            linkTarget = values.get("linkTarget", String.class);
        }

        public boolean isValid() {
            final ResourceResolver resolver = request.getResourceResolver();
            return (StringUtils.isNotBlank(linkUrl) || StringUtils.isNotBlank(linkPath))
                    && (StringUtils.isBlank(linkPath) || resolver.getResource(linkPath) != null)
                    && (StringUtils.isBlank(linkUrl) || linkUrl.matches("^https?//")
                    || !ResourceUtil.isNonExistingResource(resolver.resolve(linkUrl)));
        }

        public String getLabel() {
            return label;
        }

        public String getTitle() {
            return title;
        }

        public String getLinkUrl() {
            return StringUtils.isNotBlank(linkUrl) ? linkUrl : (linkPath + ".html");
        }

        public String getLinkTarget() {
            return linkTarget;
        }
    }

    public class WidgetModelImpl implements WidgetModel {

        protected final DashboardWidget widget;

        private transient Resource widgetResource;
        private transient String widgetPageUrl;

        public WidgetModelImpl(DashboardModelImpl dashboardModel, DashboardWidget widget) {
            this.widget = widget;
        }

        @Override
        public @NotNull String getName() {
            return widget.getName();
        }

        @Override
        public @NotNull String getLabel() {
            return widget.getLabel();
        }

        @Override
        public @NotNull String getNavTitle() {
            return widget.getNavTitle();
        }

        @Override
        public @NotNull Resource getWidgetResource() {
            if (widgetResource == null) {
                widgetResource = widget.getWidgetResource(request);
            }
            return widgetResource;
        }

        public @NotNull String getWidgetPageUrl() {
            if (widgetPageUrl == null) {
                widgetPageUrl = widget.getWidgetPageUrl(request);
            }
            return widgetPageUrl;
        }
    }

    public static final String DASHBOARD_CONTEXT = "dashboard";

    protected static final String SA_CURRENT_VIEW = DashboardModelImpl.class.getName() + "#currentView";

    @Self
    protected SlingHttpServletRequest request;

    @OSGiService
    protected DashboardManager dashboardManager;

    private transient List<NavigationItem> navigation;

    private transient List<WidgetModel> widgetModels;

    private transient DashboardWidget currentWidget;
    private transient List<String> selectors;

    @PostConstruct
    protected void init() {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final String[] requestSelectors = pathInfo.getSelectors();
        selectors = new ArrayList<>();
        if (requestSelectors.length > 0) {
            switch (requestSelectors[0]) {
                case "open":
                    setCurrentView(pathInfo.getSuffix());
                    selectors.add("view");
                    break;
                case "close":
                    setCurrentView(null);
                    selectors.add("tile");
                    break;
                default:
                    selectors = Arrays.asList(requestSelectors);
                    break;
            }
        }
        currentWidget = getWidget(getCurrentView());
    }

    public @NotNull List<NavigationItem> getNavigation() {
        if (navigation == null) {
            navigation = new ArrayList<>();
            final Resource dashboard = request.getResource();
            final Resource navItems = Optional.ofNullable(dashboard.getChild("navigation"))
                    .orElse(dashboard.getChild("jcr:content/navigation"));
            if (navItems != null) {
                for (final Resource item : navItems.getChildren()) {
                    final NavigationItem navItem = new NavigationItem(item);
                    if (navItem.isValid()) {
                        navigation.add(navItem);
                    }
                }
            }
        }
        return navigation;
    }

    protected @Nullable String getCurrentView() {
        final HttpSession session = request.getSession();
        if (session != null) {
            return (String) session.getAttribute(SA_CURRENT_VIEW);
        }
        return null;
    }

    protected void setCurrentView(@Nullable String currentView) {
        DashboardWidget widget = getWidget(currentView);
        if (widget != null) {
            final HttpSession session = request.getSession(true);
            if (session != null) {
                session.setAttribute(SA_CURRENT_VIEW, currentView);
            }
        } else {
            final HttpSession session = request.getSession();
            if (session != null) {
                session.removeAttribute(SA_CURRENT_VIEW);
            }
        }
    }

    protected @Nullable DashboardWidget getWidget(@Nullable String name) {
        if (StringUtils.isNotBlank(name)) {
            while (name.startsWith("/")) {
                name = name.substring(1);
            }
        }
        if (StringUtils.isNotBlank(name)) {
            return dashboardManager.getWidget(request, DASHBOARD_CONTEXT, name);
        }
        return null;
    }

    @Override
    public @NotNull String getTitle() {
        final Resource resource = request.getResource();
        return currentWidget != null
                ? currentWidget.getLabel()
                : resource.getValueMap().get("jcr:title", resource.getName());
    }

    @Override
    public @Nullable Resource getWidgetResource() {
        return currentWidget != null ? currentWidget.getWidgetResource(request) : null;
    }

    @Override
    public @NotNull String getSelectors() {
        return StringUtils.join(selectors, ".");
    }

    @Override
    public Collection<WidgetModel> getWidgetModels() {
        if (widgetModels == null) {
            widgetModels = new ArrayList<>();
            for (DashboardWidget widget : dashboardManager.getWidgets(request, DASHBOARD_CONTEXT)) {
                widgetModels.add(new WidgetModelImpl(this, widget));
            }
        }
        return widgetModels;
    }

    @Override
    public Collection<DashboardWidget> getWidgets() {
        return dashboardManager.getWidgets(request, DASHBOARD_CONTEXT);
    }

    @Override
    public @NotNull String getDashboardPath() {
        return StringUtils.substringBefore(request.getResource().getPath(), "/jcr:content");
    }
}
