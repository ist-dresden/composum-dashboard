package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface DashboardPlugin {

    int getRank();

    void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                        @NotNull Map<String, DashboardWidget> widgetSet);
}
