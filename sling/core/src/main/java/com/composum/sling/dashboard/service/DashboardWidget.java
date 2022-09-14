package com.composum.sling.dashboard.service;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;

public interface DashboardWidget {

    /**
     * @return the set of tools which are supported by the widget, e.g. 'dashboard', 'browser'
     */
    @NotNull Collection<String> getContext();

    /**
     * @return a set of hints what the widget is showing, e.g. 'source', 'preview', 'properties'
     */
    @NotNull Collection<String> getCategory();

    /**
     * @return the rank of the widget for automatic ordering of the available widgets
     */
    int getRank();

    /**
     * @return the identifier of the widget
     */
    @NotNull String getName();

    /**
     * @return the readable name for titles and links
     */
    @NotNull String getLabel();

    /**
     * @return an optional short label for navigation elements
     */
    @NotNull String getNavTitle();

    /**
     * Provodes the resouce, maybe a synthetic resource, which ca be used to render (include) the widget
     *
     * @param request the curren trequest
     * @return the resource to dispatch the rendering
     */
    @NotNull Resource getWidgetResource(@NotNull SlingHttpServletRequest request);

    /**
     * provides optional properties, e.g. an 'icon' for the browsers tools menu
     */
    @NotNull <T> T getProperty(@NotNull String name, @NotNull T defaultValue);

    /**
     * embeds the needed JS code
     *
     * @param writer the writer of the response
     * @param mode   the rendered view mode, e.g. 'tile', 'view', 'page'
     */
    void embedScript(@NotNull PrintWriter writer, @NotNull String mode)
            throws IOException;

    Comparator<DashboardWidget> COMPARATOR = new Comparator<>() {

        public static final String KEY_FMT = "%04d:%s";

        @Override
        public int compare(DashboardWidget o1, DashboardWidget o2) {
            return String.format(KEY_FMT, o1.getRank(), o1.getLabel())
                    .compareTo(String.format(KEY_FMT, o2.getRank(), o1.getLabel()));
        }
    };
}
