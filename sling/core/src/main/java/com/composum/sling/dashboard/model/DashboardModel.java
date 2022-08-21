package com.composum.sling.dashboard.model;

import com.composum.sling.dashboard.service.DashboardWidget;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface DashboardModel {

    interface WidgetModel {

        boolean isWidget();

        boolean isTool();
        @NotNull String getName();

        @NotNull String getLabel();

        @NotNull String getNavTitle();

        @NotNull Resource getWidgetResource();

        @NotNull String getWidgetPageUrl();
    }

    @NotNull String getTitle();

    @NotNull String getSelectors();

    @Nullable Resource getWidgetResource();

    Collection<WidgetModel> getWidgetModels();

    Collection<DashboardWidget> getWidgets();

    @NotNull String getDashboardPath();
}
