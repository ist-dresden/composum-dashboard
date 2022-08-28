package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;

public interface DashboardWidget {

    @NotNull Collection<String> getContext();

    @NotNull String getCategory();

    int getRank();

    @NotNull String getName();

    @NotNull String getLabel();

    @NotNull String getNavTitle();

    @NotNull Resource getWidgetResource(@NotNull SlingHttpServletRequest request);

    @NotNull String getWidgetPageUrl(@NotNull  SlingHttpServletRequest request);

    @Nullable <T> T getProperty(@NotNull String name, T defaultValue);

    Comparator<DashboardWidget> COMPARATOR = new Comparator<>() {

        public static final String KEY_FMT = "%04d:%s";

        @Override
        public int compare(DashboardWidget o1, DashboardWidget o2) {
            return String.format(KEY_FMT, o1.getRank(), o1.getLabel())
                    .compareTo(String.format(KEY_FMT, o2.getRank(), o1.getLabel()));
        }
    };
}
