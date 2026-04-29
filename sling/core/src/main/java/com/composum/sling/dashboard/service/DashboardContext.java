package com.composum.sling.dashboard.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

public interface DashboardContext {

    @Nullable InputStream getResourceAsStream(@NotNull String resourcePath);
}
