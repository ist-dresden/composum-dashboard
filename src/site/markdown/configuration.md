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
