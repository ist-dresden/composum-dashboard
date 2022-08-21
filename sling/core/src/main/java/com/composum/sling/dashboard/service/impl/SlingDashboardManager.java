package com.composum.sling.dashboard.service.impl;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardPlugin;
import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component(
        service = DashboardManager.class,
        name = "Composum Sling Dashboard Manager",
        immediate = true
)
public class SlingDashboardManager implements DashboardManager {

    protected static final String SA_WIDGETS = SlingDashboardManager.class.getName() + "#";

    @Reference(policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
    protected List<DashboardPlugin> dashboardPlugins;

    @Override
    public @Nullable DashboardWidget getWidget(@NotNull final SlingHttpServletRequest request,
                                               @NotNull final String name) {
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
        List<DashboardWidget> widgets = new ArrayList<>();
        for (DashboardPlugin plugin : dashboardPlugins) {
            for (DashboardWidget widget : plugin.getWidgets(request)) {
                if (StringUtils.isBlank(context) || widget.getContext().contains(context)) {
                    widgets.add(widget);
                }
            }
        }
        widgets.sort(DashboardWidget.COMPARATOR);
        return widgets;
    }
}
