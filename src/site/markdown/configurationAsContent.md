# Tools preparation as content

Another way to set up the dashboard widgets is to deploy the desired components as elements of a content page.
Configuring the desired services via [OSGI configuration](configuration.md) is also required, 
but instead of using the declared servlets via the servlet path (`sling.servlet.paths`),
the implemented resource types are used to arrange appropriate Sling components within a content page.
(Note that this could permit publishing that page to an publisher host on test systems.)

To start with that approach create a Composum Pages content page or an AEM cq:Page of the resource type

``composum/dashboard/sling``

and declare your set of Dashboard service implementations and/or
your set of content elements , e.g. ``/content/test/insights``...

## AEM example dashboard page

<details>
  <summary>example content code (/content/test/insights)</summary>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0"
          xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
          jcr:primaryType="cq:Page">
    <jcr:content
            jcr:primaryType="cq:PageContent"
            jcr:title="Dashboard"
            sling:resourceType="composum/dashboard/sling">
        <navigation
                jcr:primaryType="nt:unstructured">
            <browser
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Browser"
                    linkPath="/content/test/insights/browser">
            </browser>
            <requests
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Recent Requests"
                    linkPath="/content/test/insights/felixconsole/requests">
            </requests>
            <jcrresolver
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Resource Resolver"
                    linkPath="/content/test/insights/felixconsole/jcrresolver">
            </jcrresolver>
            <servletresolver
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Servlet Resolver"
                    linkPath="/content/test/insights/felixconsole//servletresolver">
            </servletresolver>
            <groovyconsole
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Groovy..."
                    linkUrl="/groovyconsole">
            </groovyconsole>
        </navigation>
        <generic jcr:primaryType="nt:unstructured">
        </generic>
        <widgets
                jcr:primaryType="nt:unstructured">
            <service-settings
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/service/settings">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/service/settings/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/service/settings/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/service/settings/page"/>
            </service-settings>
            <logfiles
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/logfiles">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/page"/>
                <tail
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/tail"/>
            </logfiles>
            <trace jcr:primaryType="nt:unstructured"
                   sling:resourceType="composum/dashboard/sling/trace">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/trace/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/trace/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/trace/page"/>
            </trace>
        </widgets>
    </jcr:content>
    <browser
            jcr:primaryType="cq:Page">
        <jcr:content
                jcr:primaryType="cq:PageContent"
                jcr:title="Composum Browser"
                sling:resourceType="composum/dashboard/sling/browser">
            <tree
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/browser/tree"/>
            <tool
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/browser/tool">
                <favorites
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/favorites">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/favorites/view"/>
                </favorites>
                <query
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/query">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/view"/>
                    <page
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/page"/>
                    <find
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/find"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/load"/>
                </query>
            </tool>
            <view
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/browser/view">
                <properties
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/properties">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/properties/view"/>
                    <page
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/properties/page"/>
                </properties>
                <display
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/display">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/display/view"/>
                    <form
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/display/form"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/display/load"/>
                </display>
                <caconfig
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/caconfig">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/caconfig/view"/>
                </caconfig>
                <json
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/source/json">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/json/view"/>
                    <form
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/json/form"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/json/load"/>
                </json>
                <xml
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/source/xml">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/xml/view"/>
                    <form
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/xml/form"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/xml/load"/>
                </xml>
            </view>
        </jcr:content>
    </browser>
    <felixconsole
            jcr:primaryType="cq:Page">
        <requests jcr:primaryType="cq:Page">
            <jcr:content
                    jcr:primaryType="cq:PageContent"
                    jcr:title="Sling Recent Requests"
                    sling:resourceType="composum/dashboard/sling/felixconsole/requests">
            </jcr:content>
        </requests>
        <jcrresolver jcr:primaryType="cq:Page">
            <jcr:content
                    jcr:primaryType="cq:PageContent"
                    jcr:title="Resource Resolver"
                    sling:resourceType="composum/dashboard/sling/felixconsole/jcrresolver">
            </jcr:content>
        </jcrresolver>
        <servletresolver jcr:primaryType="cq:Page">
            <jcr:content
                    jcr:primaryType="cq:PageContent"
                    jcr:title="Servlet Resolver"
                    sling:resourceType="composum/dashboard/sling/felixconsole/servletresolver">
            </jcr:content>
        </servletresolver>
    </felixconsole>
</jcr:root>
```

</details>

With this configuration the Dashboard can be opened using th URI ``/content/test/insights.html`` and
the Dashboards Browser will be available via the Dashboards menu and directly
via the URI ``/content/test/insights/browser.html``

## Composum Pages example dashboard page

Likewise, this could be used for a Composum Pages content page. For other Apache Sling based applications it could
be adapted, too - any sling resource rendered with the resurce type `composum/dashboard/sling` into a frameless HTML
page would do.

<details>
  <summary>example content code (/content/test/insights)</summary>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:cpp="http://sling.composum.com/pages/1.0"
          xmlns:jcr="http://www.jcp.org/jcr/1.0"
          xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
          xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
          jcr:primaryType="cpp:Page">
    <jcr:content
            jcr:primaryType="cpp:PageContent"
            jcr:title="Dashboard"
            sling:resourceType="composum/dashboard/sling">
        <navigation
                jcr:primaryType="nt:unstructured">
            <browser
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Browser"
                    linkPath="/content/test/insights/browser">
            </browser>
            <requests
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Recent Requests"
                    linkPath="/content/test/insights/felixconsole/requests">
            </requests>
            <jcrresolver
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Resource Resolver"
                    linkPath="/content/test/insights/felixconsole/jcrresolver">
            </jcrresolver>
            <servletresolver
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Servlet Resolver"
                    linkPath="/content/test/insights/felixconsole//servletresolver">
            </servletresolver>
            <groovyconsole
                    jcr:primaryType="nt:unstructured"
                    jcr:title="Groovy..."
                    linkUrl="/groovyconsole">
            </groovyconsole>
        </navigation>
        <generic jcr:primaryType="nt:unstructured">
        </generic>
        <widgets
                jcr:primaryType="nt:unstructured">
            <service-settings
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/service/settings">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/service/settings/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/service/settings/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/service/settings/page"/>
            </service-settings>
            <logfiles
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/logfiles">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/page"/>
                <tail
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/logfiles/tail"/>
            </logfiles>
            <trace jcr:primaryType="nt:unstructured"
                   sling:resourceType="composum/dashboard/sling/trace">
                <tile
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/trace/tile"/>
                <view
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/trace/view"/>
                <page
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/trace/page"/>
            </trace>
        </widgets>
    </jcr:content>
    <browser
            jcr:primaryType="cpp:Page">
        <jcr:content
                jcr:primaryType="cpp:PageContent"
                jcr:title="Composum Browser"
                sling:resourceType="composum/dashboard/sling/browser">
            <tree
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/browser/tree"/>
            <tool
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/browser/tool">
                <favorites
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/favorites">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/favorites/view"/>
                </favorites>
                <query
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/query">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/view"/>
                    <page
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/page"/>
                    <find
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/find"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/query/load"/>
                </query>
            </tool>
            <view
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="composum/dashboard/sling/browser/view">
                <properties
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/properties">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/properties/view"/>
                    <page
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/properties/page"/>
                </properties>
                <display
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/display">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/display/view"/>
                    <form
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/display/form"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/display/load"/>
                </display>
                <caconfig
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/caconfig">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/caconfig/view"/>
                </caconfig>
                <json
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/source/json">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/json/view"/>
                    <form
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/json/form"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/json/load"/>
                </json>
                <xml
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="composum/dashboard/sling/source/xml">
                    <view
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/xml/view"/>
                    <form
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/xml/form"/>
                    <load
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="composum/dashboard/sling/source/xml/load"/>
                </xml>
            </view>
        </jcr:content>
    </browser>
    <felixconsole
            jcr:primaryType="cpp:Page">
        <requests jcr:primaryType="cpp:Page">
            <jcr:content
                    jcr:primaryType="cpp:PageContent"
                    jcr:title="Sling Recent Requests"
                    sling:resourceType="composum/dashboard/sling/felixconsole/requests">
            </jcr:content>
        </requests>
        <jcrresolver jcr:primaryType="cpp:Page">
            <jcr:content
                    jcr:primaryType="cpp:PageContent"
                    jcr:title="Resource Resolver"
                    sling:resourceType="composum/dashboard/sling/felixconsole/jcrresolver">
            </jcr:content>
        </jcrresolver>
        <servletresolver jcr:primaryType="cpp:Page">
            <jcr:content
                    jcr:primaryType="cpp:PageContent"
                    jcr:title="Servlet Resolver"
                    sling:resourceType="composum/dashboard/sling/felixconsole/servletresolver">
            </jcr:content>
        </servletresolver>
    </felixconsole>
</jcr:root>
```

</details>
