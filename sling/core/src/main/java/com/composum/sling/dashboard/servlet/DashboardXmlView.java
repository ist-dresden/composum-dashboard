package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.service.XmlRenderer;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.composum.sling.dashboard.util.Properties;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import static com.composum.sling.dashboard.DashboardConfig.JCR_CONTENT;
import static com.composum.sling.dashboard.DashboardConfig.JCR_MIXIN_TYPES;
import static com.composum.sling.dashboard.DashboardConfig.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.NT_FILE;
import static com.composum.sling.dashboard.DashboardConfig.XML_DATE_FORMAT;
import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = {Servlet.class, DashboardWidget.class, XmlRenderer.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardXmlView.Config.class)
public class DashboardXmlView extends AbstractSourceView implements XmlRenderer, ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/source/xml";

    @ObjectClassDefinition(name = "Composum Dashboard XML Source View")
    public @interface Config {

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "xml";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 2200;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "XML";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = "Max Depth")
        int maxDepth() default 1;

        @AttributeDefinition(name = "Indent")
        int indent() default 4;

        @AttributeDefinition(name = "Source Mode")
        boolean sourceMode() default true;

        @AttributeDefinition(name = "Content Type",
                description = "the response content type for the XML content (default: 'text/plain')")
        String contentType() default "text/plain";

        @AttributeDefinition(name = "Parameter Fields",
                description = "the set of form fields to add content URL parameters")
        String[] parameterFields() default {
                "name=depth,label=depth,type=text,size=1",
                "name=raw,type=checkbox,label=source 'off'"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION)
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/form",
                DEFAULT_RESOURCE_TYPE + "/load"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_DESCRIPTION)
        String[] sling_servlet_extensions() default {
                "html",
                "xml"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_PATHS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_PATHS_DESCRIPTION)
        String[] sling_servlet_paths();
    }

    @Reference
    protected ResourceFilter resourceFilter;

    protected List<String> parameterFields;

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
        parameterFields = List.of(config.parameterFields());
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest slingRequest,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            final String mode = getHtmlMode(request, HTML_MODES);
            if (OPTION_FORM.equals(mode)) {
                sendFormFields(response, parameterFields);
            } else {
                final Resource targetResource = resourceFilter.getRequestResource(request);
                if (targetResource != null) {
                    final RequestPathInfo pathInfo = request.getRequestPathInfo();
                    final int depth = getIntParameter(request, "depth", maxDepth);
                    final boolean source = isSourceMode(request);
                    if (OPTION_LOAD.equals(mode) || "xml".equals(pathInfo.getExtension())) {
                        prepareTextResponse(response, contentType);
                        if (sourceMode) {
                            response.setHeader("Content-Disposition", "inline; filename=.content.xml");
                        }
                        final PrintWriter writer = new PrintWriter(response.getWriter());
                        try {
                            dumpXml(writer, "", targetResource, 0, depth, resourceFilter,
                                    source ? this::isAllowedProperty : resourceFilter::isAllowedProperty,
                                    source ? this::isAllowedMixin : null, null);
                        } catch (RepositoryException ignore) {
                        }
                    } else {
                        final String widgetUri = getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_LOAD);
                        if (StringUtils.isNotBlank(widgetUri)) {
                            preview(request, response, targetResource);
                        } else {
                            xmlCode(response, targetResource, depth, source);
                        }
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }
        }
    }

    protected void xmlCode(@NotNull final SlingHttpServletResponse response,
                           @NotNull final Resource targetResource, int depth, boolean source)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/display/code.html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                try (final StringWriter content = new StringWriter();
                     final PrintWriter xmlWriter = new PrintWriter(content)) {
                    dumpXml(xmlWriter, "", targetResource, 0, depth, resourceFilter,
                            source ? this::isAllowedProperty : resourceFilter::isAllowedProperty,
                            source ? this::isAllowedMixin : null, null);
                    final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                            Collections.singletonMap("content", content.toString()));
                    prepareTextResponse(response, null);
                    IOUtils.copy(reader, writer);
                } catch (RepositoryException ignore) {
                }
            }
        }
    }

    @Override
    public void dumpXml(@NotNull final PrintWriter writer, @NotNull final String indent,
                        @NotNull final Resource resource, int depth, @Nullable Integer maxDepth,
                        @NotNull final ResourceFilter resourceFilter,
                        @NotNull final Function<String, Boolean> propertyFilter,
                        @Nullable final Function<String, Boolean> mixinFilter,
                        @Nullable final Function<Object, Object> transformer)
            throws RepositoryException {
        final String name = resource.getName();
        if (depth == 0) {
            Set<String> namespaces = new TreeSet<>();
            namespaces.add("jcr");
            determineNamespaces(namespaces, resource, depth, maxDepth, resourceFilter);
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.append("<jcr:root");
            writeNamespaceAttributes(resource.getResourceResolver(), writer, namespaces);
        } else {
            writer.append(indent).append("<").append(xmlName(name));
        }
        xmlProperties(writer, indent + this.indent + this.indent, resource,
                propertyFilter, mixinFilter, transformer);
        writer.append(">\n");
        if (sourceMode && isTranslationsRootFolder(resource)) {
            dumpTranslationsFolder(writer, indent + this.indent, resource);
        } else {
            if (JCR_CONTENT.equals(name) || resource.getPath().contains("/" + JCR_CONTENT + "/")) {
                maxDepth = null;
            }
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && resourceFilter.isAllowedResource(content)) {
                dumpXml(writer, indent + this.indent, content, depth + 1, maxDepth,
                        resourceFilter, propertyFilter, mixinFilter, transformer);
            }
            if (maxDepth == null || depth + 1 < maxDepth) {
                for (final Resource child : resource.getChildren()) {
                    if (resourceFilter.isAllowedResource(child) &&
                            !(sourceMode && depth > 0 && NT_FILE.equals(child.getValueMap().get(JCR_PRIMARY_TYPE)))) {
                        final String childName = child.getName();
                        if (!JCR_CONTENT.equals(childName)) {
                            dumpXml(writer, indent + this.indent, child, depth + 1, maxDepth,
                                    resourceFilter, propertyFilter, mixinFilter, transformer);
                        }
                    }
                }
            }
        }
        if (depth == 0) {
            writer.append("</").append("jcr:root").append(">\n");
        } else {
            writer.append(indent).append("</").append(xmlName(name)).append(">\n");
        }
    }

    protected void xmlProperties(@NotNull final PrintWriter writer, @Nullable final String indent,
                                 @NotNull final Resource resource,
                                 @NotNull final Function<String, Boolean> propertyFilter,
                                 @Nullable final Function<String, Boolean> mixinFilter,
                                 @Nullable final Function<Object, Object> transformer) {
        final Map<String, Object> properties = new TreeMap<>(PROPERTY_NAME_COMPARATOR);
        for (final Map.Entry<String, Object> property : resource.getValueMap().entrySet()) {
            final String name = property.getKey();
            final Object value;
            if (propertyFilter.apply(name)
                    && (value = property.getValue()) != null
                    && !(value instanceof InputStream)) {
                if (JCR_PRIMARY_TYPE.equals(name)) {
                    xmlProperty(writer, indent, name, value);
                } else {
                    if (mixinFilter != null && JCR_MIXIN_TYPES.equals(name)) {
                        final Object values = filterValues(value, mixinFilter);
                        if (values instanceof String[] && ((String[]) values).length > 0) {
                            Arrays.sort((String[]) values);
                            properties.put(name, values);
                        }
                    } else {
                        properties.put(name, transformer != null ? transformer.apply(value) : value);
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

    protected void writeNamespaceAttributes(@NotNull final ResourceResolver resolver,
                                            @NotNull final PrintWriter writer, @NotNull final Set<String> namespaces) {
        final Session session = resolver.adaptTo(Session.class);
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
                                       int depth, @Nullable Integer maxDepth, @NotNull final ResourceFilter filter) {
        final String name = resource.getName();
        extractNamespace(keys, name);
        for (final Map.Entry<String, Object> entry : resource.getValueMap().entrySet()) {
            final String propertyName = entry.getKey();
            if (filter.isAllowedProperty(propertyName)) {
                extractNamespace(keys, propertyName);
                if (JCR_PRIMARY_TYPE.equals(propertyName)) {
                    extractNamespace(keys, entry.getValue());
                } else if (JCR_MIXIN_TYPES.equals(propertyName)) {
                    extractNamespace(keys, sourceMode
                            ? filterValues(entry.getValue(), this::isAllowedMixin) : entry.getValue());
                }
            }
        }
        if (JCR_CONTENT.equals(name) || resource.getPath().contains("/" + JCR_CONTENT + "/")
                || isTranslationsRootFolder(resource)) {
            maxDepth = null;
        }
        final Resource content = resource.getChild(JCR_CONTENT);
        if (content != null && filter.isAllowedResource(content)) {
            determineNamespaces(keys, content, depth + 1, maxDepth, filter);
        }
        if (maxDepth == null || depth + 1 < maxDepth) {
            for (final Resource child : resource.getChildren()) {
                if (filter.isAllowedResource(child) &&
                        !(sourceMode && depth > 0 && NT_FILE.equals(child.getValueMap().get(JCR_PRIMARY_TYPE)))) {
                    determineNamespaces(keys, child, depth + 1, maxDepth, filter);
                }
            }
        }
    }

    protected void extractNamespace(@NotNull final Set<String> keys, Object... values) {
        if (values != null && values.length > 0) {
            if (values.length == 1 && values[0] instanceof Object[]) {
                values = (Object[]) values[0];
            }
            for (final Object value : values) {
                if (value instanceof String) {
                    final String string = (String) value;
                    if (StringUtils.isNotBlank(string) && string.contains(":")) {
                        keys.add(StringUtils.substringBefore(string, ":"));
                    }
                }
            }
        }
    }
}
