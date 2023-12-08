# Configuration of the Composum Dashboard

There are two basic functions of the configuration:
- configuration which tools are available and the customization of these tools 
- configuration of the path at which the dashboard / browser / other pages are available.

The first part is done via OSGI configurations as discussed below. The second part can be performed two ways:
1. in the OSGI configurations the `sling.servlet.paths` can give the paths the servlets are available.
2. a content page is created that arranges the necessary servlets and their views as subresources.

When using the second variant please see the descroption of [the special content page](configurationAsContent.md), but you will still need to enable the needed tools via OSGI as below, except of providing the `sling.servlet.paths`, of course.

At set of configuration examples is delivered in the 'config' packages,
one for a Sling setup and another one for an AEM setup.

* [`sling/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum`](https://github.com/ist-dresden/composum-dashboard/tree/develop/aem/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum)
* [`aem/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum`](https://github.com/ist-dresden/composum-dashboard/tree/develop/sling/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum)

These contain the `sling.servlet.paths` settings to make the dashboard available at 
http(s)://{host}:{port}/apps/cpm/dashboard.html and the JCR browser at 
http(s)://{host}:{port}/apps/cpm/browser.html .
If the second [configuration as content page](configurationAsContent.md) is used, the likely 
should be removed from these example configurations.

*Caution:* Please remember to set appropriate ACL to make the dashboard only visible to the appropriate users.

## General configuration

Only configured services are available.
The user interface is automatically set up with the configured services.
Services that are not configured are not displayed.

Each service implements basically a servlet with a set of view or request options,
e.g. if the browsers XML view is configured via OSGi with

``com.composum.sling.dashboard.servlet.DashboardXmlView.cfg.json``...

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

This service is rendering its UI as a servlet via the configured paths with
``/apps/cpm/browser/view/xml.html`` as the main URI to access the service and a set of
servlet paths for the various UI elements of this implementation.

The same service declares a set of resource types (which can be overridden via OSGi)
through which the service can be used like a normal Sling component to embed service into the
content of the repository.

## The DashboardManager service

The service always needed to use the dashboard components is the DashboardManager service,
which provides the central settings for each of the other services, nothing works without this service.

``com.composum.sling.dashboard.service.SlingDashboardManager.cfg.json`` (AEM variant)...

```json
{
  "allowedPropertyPatterns": [
    "^.*$"
  ],
  "disabledPropertyPatterns": [
    "^rep:.*$",
    "^.*[Pp]assword.*$"
  ],
  "allowedPathPatterns": [
    "^/$",
    "^/content(/.*)?$",
    "^/conf(/.*)?$",
    "^/apps(/.*)?$",
    "^/libs(/.*)?$",
    "^/etc(/.*)?$",
    "^/var(/.*)?$",
    "^/tmp(/.*)?$",
    "^/mnt(/.*)?$",
    "^/oak:index(/.*)?$",
    "^/jcr:system(/.*)?$"
  ],
  "disabledPathPatterns": [
    ".*/rep:.*",
    "^(/.*)?/api(/.*)?$"
  ],
  "sortableTypes": [
    "nt:folder",
    "sling:Folder",
    "cq:Component"
  ],
  "cssRunmodes": [
    "author",
    "publish"
  ],
  "contentPageType": "[cq:Page]/jcr:content[cq:PageContent]",
  "loginUri": "/libs/granite/core/content/login.html"
}
```

For easy and quick setup of the dashboard browser to display the resource properties
the following services are required:

* SlingDashboardManager
* DashboardBrowserServlet
* DashboardPropertiesView


## Predefined Widgets and Tools

### The Browser

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
