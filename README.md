# [Composum](https://www.composum.com/home.html)

yet another Apache Sling based Application Platform (asap)

## Composum Dashboard

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

- [releases](https://github.com/ist-dresden/composum-dashboard/releases)

A lightweight framework for setting up a custom set of maintenance and development tools for a Sling or AEM (cloud)
platform.

### Features

- a simple page component to arrange a set of tools in a tiled view with
    - a summarized status information view on each tile
    - the option to open a detailed view for each of the arraged tools
    - the option to add some light versions of the Composum Nodes tools
        - a repository browser to inspect the JCR repository
- with a small footprint of the tools itself in the JCR repository
- customizable and restrictable via OSGi configuration for each environment
- prepared as normal content with no additional configuration needed in the web tier

### Usage

Create a content page or an AEM cq:Page of the resource type

``composum/dashboard/sling/components/page``

and your set of DasbboardPlugin service implementations and/or
your set of content elements to configure prepared DashboardWidget
implementations, e.g.
<details>
  <summary>content page example code</summary>

```xml

<jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0"
        xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
        jcr:primaryType="cq:Page">
    <jcr:content
            jcr:primaryType="cq:PageContent"
            jcr:title="Dashboard"
            sling:resourceType="composum/dashboard/sling/components/page">
        <widgets
                jcr:primaryType="nt:unstructured">
            <custom-widget
                    jcr:primaryType="nt:unstructured"
                    jcr:title="My Widget"
                    sling:resourceType="composum/dashboard/sling/components/widget"
                    widgetResourceType="my/widget/resource/type"
                    rank="{Long}500">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="my/widget/resource/type/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="my/widget/resource/type/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="my/widget/resource/type/page"/>
            </custom-widget>
            <views
                    jcr:primaryType="nt:unstructured">
                <properties
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/components/widget"
                        jcr:title="Properties"
                        context="[browser]"
                        type="hidden"
                        rank="{Long}300"
                        widgetResourceType="composum/dashboard/sling/components/properties">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/components/properties/view"/>
                    <page
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/components/properties/page"/>
                </properties>
                <preview
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/components/widget"
                        jcr:title="Preview"
                        context="[browser]"
                        type="hidden"
                        rank="{Long}400"
                        widgetResourceType="composum/dashboard/sling/components/display">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/components/display/view"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/components/display/load"/>
                </preview>
            </views>
        </widgets>
    </jcr:content>
    <browser
            jcr:primaryType="nt:unstructured"
            sling:resourceType="composum/dashboard/sling/components/widget"
            jcr:title="Composum Browser"
            navTitle="Browser"
            type="tool"
            rank="{Long}300"
            widgetPageUrl="${path}.html"
            widgetResourceType="composum/dashboard/sling/components/browser">
        <view
                jcr:primaryType="nt:unstructured"
                sling:resourceType="composum/dashboard/sling/components/browser/view"/>
        <tree
                jcr:primaryType="nt:unstructured"
                sling:resourceType="composum/dashboard/sling/components/browser/tree"/>
    </browser>
</jcr:root>
```

</details>

In this example the widget are declared by the predefined resource type

``composum/dashboard/sling/components/widget``

which is used by the predefined GenerigDashboradWidgetPlugin. This plugin is also activated by an
appropriate OSGi factory configuration:
<details>
  <summary>com.composum.sling.dashboard.service.GenericDashboardPlugin~default-widgets.cfg.json</summary>

```json
{
  "resourceType": "composum/dashboard/sling/components/widget",
  "searchRoot": "/content"
}
```
</details>

and collects all contents ellements with the configured resource type. These widgets are available the via the
DashboardManager service.

A single dasboard widget is normally implemented as a servlet whitch declares the needed resource types and
which is configured via OSGi. This configuration should be required to ensure that such a servlet is
active only if it should be available on an environment.

### Predefined Widgets and Tools

#### The Composum Browser

The browser implementation is a widget of type 'tool'. The browser implements a 'page' resource type and
for internal use a 'tree' and a 'view' resource type. The tree implementation is part of the browser widget
but the view implementations not. The available views itself are also widgets implemented by their own servlets.
The browser determines the available views by requesting the avaialable widgets declared for the context 'browser'.
Each such widget avaialable via the DashboardManager is shown in the browsers view section.

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardBrowserServlet.cfg.json</summary>

```json
{
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
  "loginUri": "/libs/granite/core/content/login.html"
}
```
</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardPropertiesView.cfg.json</summary>

```json
{
  "allowedPropertyPatterns": [
    "^.*$"
  ],
  "disabledPropertyPatterns": [
    "^rep:.*$",
    "^.*[Pp]assword.*$"
  ]
}
```
</details>

<details>
  <summary>com.composum.sling.dashboard.servlet.DashboardDisplayView.cfg.json</summary>

```json
{}
```
</details>


### see also

#### [Composum Nodes](https://github.com/ist-dresden/composum-nodes)

the big brother of the Composum Dasboard tools

* [Composum Nodes Homepage](https://www.composum.com/home/nodes.html)

---

#### [Composum Pages](https://github.com/ist-dresden/composum-pages)

* [Composum Pages Homepage](https://www.composum.com/home/pages.html)

#### [Composum Assets](https://github.com/ist-dresden/composum-assets)

* [Composum Homepage](https://www.composum.com/home.html)

#### [Composum Platform](https://github.com/ist-dresden/composum-platform)

* [Composum Platform Homepage](https://www.composum.com/home.html)
