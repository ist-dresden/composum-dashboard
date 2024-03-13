# Composum Dashboard Download and Installation

Depending on your platform you will need to install one of the following packages:

- [The complete AEM package for the dashboard tools framework](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-aem)
- [The complete Sling Package for the dashboard tools framework](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-sling)

All widgets and views are generally disabled until there is an OSGI configuration for them, so some configuration is
mandatory.

## Quick configuration

For quick testing, a set of configurations that displays most widgets can be created using the appropriate following
packages as an example:

- [A package with example configurations for AEM](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-aem-osgi-config/1.2.14)
- [A package with example configurations for Sling](https://central.sonatype.com/artifact/com.composum.dashboard/composum-dashboard-sling-osgi-config/1.2.14)

Please note that the example configurations have the path
`/apps/composum/dashboard/config.composum` containing a run mode suffix 'composum'. You could
either add a run mode "composum" to the system or rename that path to something like `config.author.dev`
or move / copy the configurations appropriately.

With the example configurations, the dashboard appears at http(s)://{host}:{port}/apps/cpm/dashboard.html - 
the URL itself is configurable.

## Detailed configuration for production use

If you want to choose yourself the URL, widget visibilities and functionality details of the widgets, you could
copying and adapting the appropriate configurations from these packages or the source of these packages:

- [AEM configurations](https://github.com/ist-dresden/composum-dashboard/tree/develop/aem/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum)
- [Sling configurations](https://github.com/ist-dresden/composum-dashboard/tree/develop/sling/config/src/main/content/jcr_root/apps/composum/dashboard/config.composum)

The URL of the dashboard's elements can be either configured via OSGI or via a content page -
for details see the [section on configuration](configuration.md).
