package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface DashboardManager extends ResourceFilter {

    @Nullable DashboardWidget getWidget(@NotNull SlingHttpServletRequest request,
                                        @Nullable String context, @NotNull String name);

    Collection<DashboardWidget> getWidgets(@NotNull SlingHttpServletRequest request,
                                           @Nullable String context);

    boolean isSortableType(@Nullable String type);

    @NotNull String getLoginUri();

    boolean createContentPage(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response,
                              @NotNull String path, @Nullable ContentGenerator generator, String... jsonContent);
}
