package com.composum.sling.dashboard.service;

import com.google.gson.stream.JsonWriter;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Function;

public interface JsonRenderer extends SourceRenderer {

    void dumpJson(@NotNull JsonWriter writer,
                  @NotNull Resource resource, int depth, @Nullable Integer maxDepth,
                  @NotNull ResourceFilter resourceFilter,
                  @NotNull Function<String, Boolean> propertyFilter,
                  @Nullable Function<String, Boolean> mixinFilter)
            throws IOException;
}
