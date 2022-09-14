package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ContentGenerator {

    @Nullable Resource createContent(@NotNull SlingHttpServletRequest request, @NotNull Resource parent,
                                     @NotNull String name, @NotNull String primaryType)
            throws PersistenceException;
}
