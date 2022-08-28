package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * the browser is providing general restrictions for the display plugins
 */
public interface DashboardBrowser extends DashboardPlugin {

    /**
     * checks if a given resource is allowed by the configured restrictions
     *
     * @param resource the resource to check
     * @return 'true' if the resource can be displayed
     */
    boolean isAllowedResource(@NotNull Resource resource);

    /**
     * retrieves the target resource to show if it is an allowed resource
     *
     * @param request the current reuest
     * @return the resource or 'null' if the resource doesn't exist or is not allowed
     */
    @Nullable Resource getRequestResource(@NotNull SlingHttpServletRequest request);
}
