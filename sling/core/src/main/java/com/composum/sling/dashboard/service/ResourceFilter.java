package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ResourceFilter {

    boolean isAllowedProperty(@NotNull String name);

    /**
     * checks if a given resource is allowed by the configured restrictions
     *
     * @param resource the resource to check
     * @return 'true' if the resource can be displayed
     */
    boolean isAllowedResource(@NotNull Resource resource);

    /**
     * @param request the current HTTP request
     * @return the requested resource if this resource is allowed, otherwise 'null'
     */
    @Nullable Resource getRequestResource(@NotNull SlingHttpServletRequest request);
}
