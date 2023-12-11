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

## Download and Installation

Depending on your platform you will need to install one of the following packages:

- [The complete AEM package for the dashboard tools framework](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-aem)
- [The complete Sling Package for the dashboard tools framework](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-sling)

Since all widgets and views are generally disabled untilthere is an OSGI configuration for them. These 
can be created using the appropriate following packages as an example:

- [A package with example configurations for AEM](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-aem-osgi-config/1.2.14)
- [A package with example configurations for Sling](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-sling-osgi-config/1.2.14)

or copying and adapting the appropriate configurations from the source of these packages:

- [AEM configurations at /apps/composum/dashboard/config.composum](https://github.com/ist-dresden/composum-dashboard/tree/develop/aem/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum)
- [Sling configurations at ](https://github.com/ist-dresden/composum-dashboard/tree/develop/sling/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum)

If you use the packages: please note that the path contains a runlevel suffix 'composum' - either this runlevel is 
added to the system, or the configurations have to be moved appropriately.

The configurations make the dashboard available at http(s)://{host}:{port}/apps/cpm/dashboard.html and the JCR browser 
available at http(s)://{host}:{port}/apps/cpm/browser.html . 
Please notice that the URL of the dashboard's elements can 
be either configured via OSGI or via a content page - for details see the [section on configuration](configuration.md).


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
