package com.composum.sling.dashboard.service;

import org.jetbrains.annotations.NotNull;

public interface SourceRenderer {

    boolean isAllowedProperty(@NotNull String name);

    boolean isAllowedMixin(@NotNull String value);
}
