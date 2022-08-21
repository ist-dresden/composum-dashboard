package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface DashboardPlugin {

    Collection<DashboardWidget> getWidgets(@NotNull final SlingHttpServletRequest request);
}
