# Configuration of the JCR Browser

The browser implementation is a widget of type 'tool'. The browser implements a 'page' resource type and,
for internal use, a 'tree', a 'tool' and a 'view' with a 'form' resource type.
The tree implementation is part of the browser widget, but the view implementations are not.

The available views themselves are also widgets implemented by their own servlets.
The browser determines the available views by querying the available widgets declared for the Browser context.
Each such widget available through the DashboardManager is displayed in the browser's view pane.

In addition, the browser arranges the service implementations of the 'Tool' category in the browser's tool menu,
which is displayed in the browser above the view pane.

A complete Browser with all predefined element consists of the services:

* SlingDashboardManager
* DashboardBrowserServlet

with the view components:

* DashboardPropertiesView

  to show the resource properties as table with links to follow the path properties

* DashboardDisplayView

  to show the resources rendered as HTML (component based resources) or in the appropriate format (assets)

* DashboardCaConfigView

  to show a set of configured CA configuration settings in the context of the current resource

* DashboardJsonView

  to show the current resource as JSON source code

* DashboardXmlView

  to show the current resource as XML content source code

and the tools

* DashboardFavoritesTool

  to mark resources as favorites and provide these favorites in configured short lists

* DashboardQueryWidget

  to search resources using SQL2, XPath or Text queries

With the following configuration you can be opened the Browser using the URI ``/apps/cpm/browser.html``
If you have prepared the browser as content as described above, both URIs can be used to open the browser.

If you do not want to provide the services through servlet paths,
you can remove all "sling.servlet.paths" from the OSGi configuration.
In that case you must prepare appropriate content for the Dashboard views.

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardBrowserServlet.cfg.json</summary>

```json
{
  "sling.servlet.paths": [
    "/apps/cpm/browser",
    "/apps/cpm/browser/page",
    "/apps/cpm/browser/view",
    "/apps/cpm/browser/form",
    "/apps/cpm/browser/tool",
    "/apps/cpm/browser/tree"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardPropertiesView.cfg.json</summary>

```json
{
  "sling.servlet.paths": [
    "/apps/cpm/browser/view/properties",
    "/apps/cpm/browser/view/properties/view"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardDisplayView.cfg.json</summary>

```json
{
  "loadDocuments": true,
  "parameterFields": [
    "name=wcmmode,label=wcmmode,type=select,options=:--|disabled|edit|preview"
  ],
  "sling.servlet.paths": [
    "/apps/cpm/browser/view/display",
    "/apps/cpm/browser/view/display/view",
    "/apps/cpm/browser/view/display/form",
    "/apps/cpm/browser/view/display/load"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardCaConfigView.cfg.json</summary>

```json
{
  "inspectedConfigurations": [
    "<your CA configuration implementation class names...>"
  ],
  "inspectedConfigurationCollections": [
    "<your CA configuration collection implementation class names...>"
  ],
  "sling.servlet.paths": [
    "/apps/cpm/browser/view/caconfig",
    "/apps/cpm/browser/view/caconfig/view"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardJsonView.cfg.json</summary>

```json
{
  "maxDepth": 0,
  "sourceMode": true,
  "sling.servlet.paths": [
    "/apps/cpm/browser/view/json",
    "/apps/cpm/browser/view/json/view",
    "/apps/cpm/browser/view/json/form",
    "/apps/cpm/browser/view/json/load"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardXmlView.cfg.json</summary>

```json
{
  "maxDepth": 0,
  "sourceMode": true,
  "sling.servlet.paths": [
    "/apps/cpm/browser/view/xml",
    "/apps/cpm/browser/view/xml/view",
    "/apps/cpm/browser/view/xml/form",
    "/apps/cpm/browser/view/xml/load"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardFavoritesTool.cfg.json</summary>

```json
{
  "favoriteGroups": [
    "Pages=^/content/(?!(dam)/.*$)",
    "Assets=^/content/(dam)/.*$"
  ],
  "sling.servlet.paths": [
    "/apps/cpm/browser/favorites",
    "/apps/cpm/browser/favorites/view"
  ]
}
```

</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardQueryWidget.cfg.json</summary>

```json
{
  "queryTemplates": [
    "<your set of frequently used queries for your project...>"
  ],
  "maxResults": 500,
  "sling.servlet.paths": [
    "/apps/cpm/query",
    "/apps/cpm/query/page",
    "/apps/cpm/query/view",
    "/apps/cpm/query/find"
  ]
}
```

</details>
