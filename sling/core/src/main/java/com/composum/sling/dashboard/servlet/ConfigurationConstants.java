package com.composum.sling.dashboard.servlet;

/**
 * Common constants for the configurations, so that we share as much common text as possible.
 */
public interface ConfigurationConstants {

    String CFG_NAME_NAME = "Name";
    String CFG_NAME_DESCRIPTION = "An ID for the widget.";

    String CFG_CONTEXT_NAME = "Context";
    String CFG_CONTEXT_DESCRIPTION = "The context where the widget is available - e.g. 'browser' or 'dashboard'. " +
            "Relevant only when the dashboard is configured using servlet paths.";

    String CFG_CATEGORY_NAME = "Category";
    String CFG_CATEGORY_DESCRIPTION = "The category of a widget - in the browser e.g. 'favorites', 'tool', 'search'. " +
            "Relevant only when the dashboard is configured using servlet paths.";

    String CFG_RANK_NAME = "Rank";
    String CFG_RANK_DESCRIPTION = "The rank is used for ordering widgets / views. " +
            "Relevant only when the dashboard is configured using servlet paths.";

    String CFG_LABEL_NAME = "Label";
    String CFG_LABEL_DESCRIPTION = "The human readable widget label.";

    String CFG_NAVIGATION_NAME = "Navigation Title";

    String CFG_RESOURCE_TYPE_NAME = "Resource Types";
    String CFG_RESOURCE_TYPE_DESCRIPTION = "The resource types implemented by this servlet. " +
            "Relevant only when the it is rendered using a content page.";

    String CFG_SERVLET_EXTENSIONS_NAME = "Servlet Extensions";
    String CFG_SERVLET_EXTENSIONS_DESCRIPTION = "The possible extensions supported by this servlet.";

    String CFG_SERVLET_PATHS_NAME = "Servlet Paths";
    String CFG_SERVLET_PATHS_DESCRIPTION = "The servlet paths if this configuration variant should be supported. " +
            "Alternatively, the servlet can be rendered from a special content page using it's resource type(s).";

}
