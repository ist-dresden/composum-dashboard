package com.composum.sling.dashboard.service;

import com.google.gson.stream.JsonWriter;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface JsonRenderer {

    void dumpJson(@NotNull JsonWriter writer, @NotNull Resource resource, int depth, @Nullable Integer maxDepth)
            throws IOException;
}
