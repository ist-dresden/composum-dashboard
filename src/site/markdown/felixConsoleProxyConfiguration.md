# Felix Console Proxy Configuration

There are many valuable light weight inspection views like the "Recent Requests" in the Felix console
that are very helpful tools for development and operations, 
but are sometimes not easily available even in development and testing environments, if at all.
Thus, the dashboard has a feature that allows selectively choosing some views and making them available through the 
dashboard. Please note that not all views are supported, and according to the lightweight read only philosophy of 
the Composum Dashboard, POST requests are not supported, which limits the use of some otherwise supported views.

In the [example configuration packages](configuration.md) there are currently 3 views:
- Sling Recent Requests
- Sling Resource Resolver (only the display, the form doesn't work because it needs a POST request)
- Sling Servlet Resolver

Most "Status" views and many of the other views should work - please [contact us](https://www.composum.com/home/contact.html) if there are views that do not yet but might be fixed.

<b>Caution</b>: please take care not to use views that could impact your system or change configurations!

For the configuration of such views there is the configuration factory `Composum Dashboard Felix Console Proxy` 
(PID com.composum.sling.dashboard.servlet.DashboardFelixConsoleProxyServlet). This is an example configuration that
makes the "Recent Requests" views available:

```json
{
  "sling.servlet.resourceTypes": [
    "composum/dashboard/sling/felixconsole/requests"
  ],
  "name": "requests",
  "label": "Sling Recent Requests",
  "sling.servlet.paths": [
    "/apps/cpm/felixconsole/requests"
  ],
  "rank:Integer": 7100,
  "proxied.webconsole.label": "requests"
}
```

| Property Key           | Description                                                                                               |
| ---------------------- | --------------------------------------------------------------------------------------------------------- |
| name                   | An ID for the widget.                                                                                     |
| proxied.webconsole.label | The label of the servlet in the Felix Console that we are proxying for the dashboard - e.g. 'requests' for /system/console/requests |
| rank                   | The rank is used for ordering widgets / views. Relevant only when the dashboard is configured using servlet paths. |
| label                  | The human readable widget label.                                                                          |
| sling.servlet.resourceTypes | The resource types implemented by this servlet. Relevant only when it is rendered using a content page.       |
| sling.servlet.extensions | The possible extensions supported by this servlet.                                                         |
| sling.servlet.paths     | The servlet paths if this configuration variant should be supported. Alternatively, the servlet can be rendered from a special content page using it's resource type(s). |

You might want to integrate the views you need into the [navigation of the dashboard](configuration.md).
