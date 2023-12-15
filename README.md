# [Composum](https://www.composum.com/home.html)

an Apache Sling based Application Platform

## Composum Dashboard

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

- [Releases](https://github.com/ist-dresden/composum-dashboard/releases)
- [Documentation site for Composum Dashboard](https://ist-dresden.github.io/composum-dashboard/)

A lightweight framework for setting up a custom set of maintenance and development tools for a Sling or AEM (cloud)
platform, including several tools such as a lightweight JCR browser and a log viewer.

### Features

- a simple page component to arrange a set of read only tools in a tiled view with
    - a summarized status information view on each tile
    - the option to open a detailed view for each of the arranged tools
    - the option to add some light versions of the [Composum Nodes](https://github.com/ist-dresden/composum-nodes) tools:
        - [a repository browser](https://ist-dresden.github.io/composum-dashboard/browserConfiguration.html) to inspect the JCR repository
        - a log file viewer
        - [a proxy](https://ist-dresden.github.io/composum-dashboard/felixConsoleProxyConfiguration.html) for debugging views of the Felix Console
- with a small footprint of the tools itself in the JCR repository
- customizable and restrictable via OSGi configuration for each environment
- prepared as normal content with no additional configuration needed in the web tier
- works even on an AEM cloud publisher (though that's likely best only for internal testing systems)

For a more detailed description of the features, installation instructions etc. 
please consult the [project documentation site](https://ist-dresden.github.io/composum-dashboard/).

[![Image of the Dashboard](src/site/resources/image/Dashboard.thumb.png)](src/site/resources/image/Dashboard.png)

[Picture of the dashboard - click to enlarge](src/site/resources/image/Dashboard.png)

[![Image of the JCR Browser](src/site/resources/image/JcrBrowser.thumb.png)](src/site/resources/image/JcrBrowser.png)

[Picture of the JCR Browser - click to enlarge](src/site/resources/image/JcrBrowser.png)

### see also

#### [Composum Nodes](https://github.com/ist-dresden/composum-nodes)

the big brother of the Composum Dashboard tools

* [Composum Nodes Homepage](https://www.composum.com/home/nodes.html)

---

#### [Composum Pages](https://github.com/ist-dresden/composum-pages)

* [Composum Pages Homepage](https://www.composum.com/home/pages.html)

#### [Composum Assets](https://github.com/ist-dresden/composum-assets)

* [Composum Homepage](https://www.composum.com/home.html)

#### [Composum Platform](https://github.com/ist-dresden/composum-platform)

* [Composum Platform Homepage](https://www.composum.com/home.html)
