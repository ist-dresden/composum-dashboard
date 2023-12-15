# Composum Dashboard

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

- [Releases](https://github.com/ist-dresden/composum-dashboard/releases)

A lightweight framework for setting up a custom set of maintenance and development tools for a Sling or AEM (cloud) 
platform, including several tools like a lightweight JCR browser and a log viewer. 

## Features

- a simple page component to arrange a set of tools in a tiled view with
    - a summarized status information view on each tile
    - the option to open a detailed view for each of the arranged tools
    - the option to add some light versions of the Composum Nodes tools
        - a repository browser to inspect the JCR repository
- with a small footprint of the tools itself in the JCR repository
- customizable and restrictable via OSGi configuration for each environment
- prepared as normal content with no additional configuration needed in the web tier
- works even on an AEM cloud publisher (though only recommended for testing systems)

## Provided components

Out of the box are currently provided the following tools and widgets, with all widgets views optional and configurable:

1. **Dashboard:** Easy organization of tool widgets in a tiled layout, offering both summarized and detailed views for each tool. As widgets are currently available:
   - some service settings
   - a logfile view
2. **JCR Repository Browser:** A lightweight read-only repository browser for inspecting the JCR repository with a minimal footprint, including properties view, preview, JSON view, XML view, SQL2 / XPATH query tool, favorites list

[![Image of the Dashboard](image/Dashboard.thumb.png)](image/Dashboard.png)

[Picture of the Dashboard - click to enlarge](image/Dashboard.png)

[![Image of the JCR Browser](image/JcrBrowser.thumb.png)](image/JcrBrowser.png)

[Picture of the JCR Browser - click to enlarge](image/JcrBrowser.png)

For [installation](installation.md) and [configuration](configuration.md) see the corresponding sections of this site.

## See also

* [Composum Homepage](https://www.composum.com/home.html)
* [Composum Nodes](https://github.com/ist-dresden/composum-nodes) - the big brother of the Composum Dashboard tools 
  / [Composum Nodes Homepage](https://www.composum.com/home/nodes.html)
* [Composum Pages](https://github.com/ist-dresden/composum-pages) / [Composum Pages Homepage](https://www.composum.
  com/home/pages.html)
* [Composum Assets](https://github.com/ist-dresden/composum-assets)
* [Composum Platform](https://github.com/ist-dresden/composum-platform) 
  / [Composum Platform Homepage](https://www.composum.com/home.html)
* [Composum AI](https://ist-dresden.github.io/composum-AI/) for AEM / Composum Pages
