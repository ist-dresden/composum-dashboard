package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface DashboardManager {

    @Nullable DashboardWidget getWidget(@NotNull SlingHttpServletRequest request, @NotNull String name);

    Collection<DashboardWidget> getWidgets(@NotNull SlingHttpServletRequest request, @Nullable String context);
}
