package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.Properties;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardXmlView.Config.class)
public class DashboardXmlView extends AbstractSourceView implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/source/xml";

    @ObjectClassDefinition(name = "Composum Dashboard XML Source View")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "xml";

        @AttributeDefinition(name = "Context")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category();

        @AttributeDefinition(name = "Rank")
        int rank() default 2200;

        @AttributeDefinition(name = "Label")
        String label() default "XML";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Max Depth")
        int maxDepth() default 0;

        @AttributeDefinition(name = "Indent")
        int indent() default 4;

        @AttributeDefinition(name = "Source Mode")
        boolean sourceMode() default true;

        @AttributeDefinition(name = "Content Type",
                description = "the response content type for the XML content (default: 'text/plain')")
        String contentType() default "text/plain";

        @AttributeDefinition(name = "Resource Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/load"
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "html",
                "xml"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servletd paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    @Reference
    protected ResourceFilter resourceFilter;

    @Override
    protected @NotNull ResourceFilter getResourceFilter() {
        return resourceFilter;
    }

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        maxDepth = config.maxDepth();
        indent = StringUtils.repeat(" ", config.indent());
        sourceMode = config.sourceMode();
        contentType = config.contentType();
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final Resource targetResource = resourceFilter.getRequestResource(request);
        if (targetResource != null) {
            final String mode = getHtmlMode(request, HTML_MODES);
            if (OPTION_LOAD.equals(mode) || "xml".equals(pathInfo.getExtension())) {
                prepareTextResponse(response, contentType);
                if (sourceMode) {
                    response.setHeader("Content-Disposition", "inline; filename=.content.xml");
                }
                final PrintWriter writer = new PrintWriter(response.getWriter());
                try {
                    dumpXml(request, writer, "", targetResource, 0, maxDepth);
                } catch (RepositoryException ignore) {
                }
            } else {
                final String widgetUri = getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_LOAD);
                if (StringUtils.isNotBlank(widgetUri)) {
                    preview(request, response, targetResource);
                } else {
                    xmlCode(request, response, targetResource);
                }
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    protected void xmlCode(@NotNull final SlingHttpServletRequest request,
                           @NotNull final SlingHttpServletResponse response,
                           @NotNull final Resource targetResource)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/display/code.html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                try (final StringWriter content = new StringWriter();
                     final PrintWriter xmlWriter = new PrintWriter(content)) {
                    dumpXml(request, xmlWriter, "", targetResource, 0, maxDepth);
                    final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                            Collections.singletonMap("content", content.toString()));
                    prepareTextResponse(response, null);
                    IOUtils.copy(reader, writer);
                } catch (RepositoryException ignore) {
                }
            }
        }
    }

    public void dumpXml(@NotNull final SlingHttpServletRequest request,
                        @NotNull final PrintWriter writer, @Nullable final String indent,
                        @NotNull final Resource resource, int depth, @Nullable Integer maxDepth)
            throws RepositoryException {
        final String name = resource.getName();
        if (depth == 0) {
            Set<String> namespaces = new TreeSet<>();
            namespaces.add("jcr");
            determineNamespaces(namespaces, resource, depth, maxDepth);
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.append("<jcr:root");
            writeNamespaceAttributes(request, writer, namespaces);
        } else {
            writer.append(indent).append("<").append(xmlName(name));
        }
        xmlProperties(writer, indent + this.indent + this.indent, resource);
        writer.append(">\n");
        if (sourceMode && isTranslationsRootFolder(resource)) {
            dumpTranslationsFolder(writer, indent + this.indent, resource);
        } else {
            if (JCR_CONTENT.equals(name) || resource.getPath().contains("/" + JCR_CONTENT + "/")) {
                maxDepth = null;
            }
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && resourceFilter.isAllowedResource(content)) {
                dumpXml(request, writer, indent + this.indent, content, depth + 1, maxDepth);
            }
            if (maxDepth == null || depth < maxDepth) {
                for (final Resource child : resource.getChildren()) {
                    if (resourceFilter.isAllowedResource(child)) {
                        final String childName = child.getName();
                        if (!JCR_CONTENT.equals(childName)) {
                            dumpXml(request, writer, indent + this.indent, child, depth + 1, maxDepth);
                        }
                    }
                }
            }
        }
        if (depth == 0) {
            writer.append("</").append("jcr:root").append(">\n");
        } else {
            writer.append(indent).append("</").append(name).append(">\n");
        }
    }

    protected void xmlProperties(@NotNull final PrintWriter writer, @Nullable final String indent,
                                 @NotNull final Resource resource) {
        final Map<String, Object> properties = new TreeMap<>(PROPERTY_NAME_COMPARATOR);
        for (final Map.Entry<String, Object> property : resource.getValueMap().entrySet()) {
            final String name = property.getKey();
            final Object value = property.getValue();
            if (isAllowedProperty(name) && value != null && !(value instanceof InputStream)) {
                if (JCR_PRIMARY_TYPE.equals(name)) {
                    xmlProperty(writer, indent, name, value);
                } else {
                    if (sourceMode && JCR_MIXIN_TYPES.equals(name)) {
                        final Object values = filterValues(value, NON_SOURCE_MIXINS);
                        if (values instanceof String[] && ((String[]) values).length > 0) {
                            Arrays.sort((String[]) values);
                            properties.put(name, values);
                        }
                    } else {
                        properties.put(name, value);
                    }
                }
            }
        }
        for (final Map.Entry<String, Object> property : properties.entrySet()) {
            xmlProperty(writer, indent, property.getKey(), property.getValue());
        }
    }

    protected void xmlProperty(@NotNull final PrintWriter writer, @Nullable final String indent,
                               @NotNull final String name, @Nullable final Object value) {
        writer.append("\n").append(indent)
                .append(xmlName(name))
                .append("=\"").append(Properties.xmlType(value));
        Properties.toXml(writer, value, XML_DATE_FORMAT);
        writer.append("\"");
    }

    protected String xmlName(@NotNull final String name) {
        return ISO9075.encode(name.replaceAll("\\s+", "_"));
    }

    protected void dumpTranslationsFolder(@NotNull final PrintWriter writer, @Nullable final String indent,
                                          @NotNull final Resource folder) {
        final Set<String> entryKeys = new TreeSet<>();
        final Set<String> folderKeys = new TreeSet<>();
        for (final Resource item : folder.getChildren()) {
            final ValueMap values = item.getValueMap();
            if ("sling:MessageEntry".equals(values.get(JCR_PRIMARY_TYPE, String.class))) {
                entryKeys.add(item.getName());
            } else {
                folderKeys.add(item.getName());
            }
        }
        for (final String name : entryKeys) {
            final Resource item = folder.getChild(name);
            if (item != null) {
                final ValueMap props = item.getValueMap();
                writer.append(indent).append("<").append(xmlName(name))
                        .append(" ").append(JCR_PRIMARY_TYPE).append("=\"").append(Properties.xmlString(
                                props.get(JCR_PRIMARY_TYPE, "sling:MessageEntry"))).append("\"\n")
                        .append(indent).append(this.indent)
                        .append("sling:key=\"").append(Properties.xmlString(
                                props.get("sling:key", name))).append("\"\n")
                        .append(indent).append(this.indent)
                        .append("sling:message=\"").append(Properties.xmlString(
                                props.get("sling:message", ""))).append("\"/>\n");
            }
        }
        for (final String name : folderKeys) {
            final Resource item = folder.getChild(name);
            if (item != null) {
                final ValueMap props = item.getValueMap();
                writer.append(indent).append("<").append(xmlName(name))
                        .append(" ").append(JCR_PRIMARY_TYPE).append("=\"").append(Properties.xmlString(
                                props.get(JCR_PRIMARY_TYPE, "sling:Folder"))).append("\">\n");
                dumpTranslationsFolder(writer, indent + this.indent, item);
                writer.append(indent).append("</").append(name).append(">\n");
            }
        }
    }

    protected void writeNamespaceAttributes(@NotNull final SlingHttpServletRequest request,
                                            @NotNull final PrintWriter writer, @NotNull final Set<String> namespaces) {
        final Session session = request.getResourceResolver().adaptTo(Session.class);
        if (session != null) {
            int index = 0;
            for (final String ns : namespaces) {
                try {
                    String nsUri = session.getNamespaceURI(ns);
                    if (StringUtils.isNotBlank(nsUri)) {
                        writer.append(" xmlns:").append(ns).append("=\"").append(nsUri).append("\"");
                        if (++index < namespaces.size()) {
                            writer.append("\n         ");
                        }
                    }
                } catch (RepositoryException ignore) {
                }
            }
        }
    }

    protected void determineNamespaces(@NotNull final Set<String> keys, @NotNull final Resource resource,
                                       int depth, @Nullable Integer maxDepth) {
        final String name = resource.getName();
        addNamespace(keys, name);
        for (final String propertyName : resource.getValueMap().keySet()) {
            if (isAllowedProperty(propertyName)) {
                addNamespace(keys, propertyName);
            }
        }
        if (JCR_CONTENT.equals(name) || resource.getPath().contains("/" + JCR_CONTENT + "/")
                || isTranslationsRootFolder(resource)) {
            maxDepth = null;
        } else {
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && resourceFilter.isAllowedResource(content)) {
                determineNamespaces(keys, content, depth + 1, maxDepth);
            }
        }
        if (maxDepth == null || depth < maxDepth) {
            for (final Resource child : resource.getChildren()) {
                if (resourceFilter.isAllowedResource(child)) {
                    determineNamespaces(keys, child, depth + 1, maxDepth);
                }
            }
        }
    }

    protected void addNamespace(Set<String> keys, String name) {
        if (StringUtils.isNotBlank(name) && name.contains(":")) {
            keys.add(StringUtils.substringBefore(name, ":"));
        }
    }
}