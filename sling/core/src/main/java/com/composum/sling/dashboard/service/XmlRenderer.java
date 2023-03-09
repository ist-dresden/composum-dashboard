package com.composum.sling.dashboard.service;

import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.jcr.RepositoryException;
import java.io.PrintWriter;
import java.util.function.Function;

public interface XmlRenderer extends SourceRenderer {

    void dumpXml(@NotNull PrintWriter writer, @NotNull String indent,
                 @NotNull Resource resource, int depth, @Nullable Integer maxDepth,
                 @NotNull final ResourceFilter resourceFilter,
                 @NotNull final Function<String, Boolean> propertyFilter,
                 @Nullable final Function<String, Boolean> mixinFilter,
                 @Nullable final Function<Object, Object> transformer)
            throws RepositoryException;
}
