package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface DashboardPlugin {

    int getRank();

    /**
     * Adds the appropriate widgets provides by this plugin to the given widget set.
     *
     * @param request   the current request
     * @param context   the context to filter the appropriate ones
     * @param widgetSet the set of widgets to fill
     */
    void provideWidgets(@NotNull SlingHttpServletRequest request, @Nullable final String context,
                        @NotNull Map<String, DashboardWidget> widgetSet);
}
