package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.JsonRenderer;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.composum.sling.dashboard.util.Properties;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import static com.composum.sling.dashboard.DashboardConfig.JSON_DATE_FORMAT;
import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = {Servlet.class, DashboardWidget.class, JsonRenderer.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardJsonView.Config.class)
public class DashboardJsonView extends AbstractSourceView implements JsonRenderer, ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/source/json";

    @ObjectClassDefinition(name = "Composum Dashboard Json Source View")
    public @interface Config {

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "json";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 2000;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "JSON";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = "Max Depth")
        int maxDepth() default 1;

        @AttributeDefinition(name = "Indent")
        int indent() default 2;

        @AttributeDefinition(name = "Source Mode")
        boolean sourceMode() default true;

        @AttributeDefinition(name = "Content Type",
                description = "the response content type for the JSON content (default: 'application/json')")
        String contentType() default "application/json";

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
                "json"
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
                    if (OPTION_LOAD.equals(mode) || "json".equals(pathInfo.getExtension())) {
                        prepareTextResponse(response, contentType);
                        final JsonWriter writer = new JsonWriter(response.getWriter());
                        writer.setIndent(this.indent);
                        dumpJson(writer, targetResource, 0, depth, resourceFilter,
                                source ? this::isAllowedProperty : resourceFilter::isAllowedProperty,
                                source ? this::isAllowedMixin : null, null);
                    } else {
                        final String widgetUri = getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_LOAD);
                        if (StringUtils.isNotBlank(widgetUri)) {
                            preview(request, response, targetResource);
                        } else {
                            jsonCode(response, targetResource, depth, source);
                        }
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }
        }
    }

    protected void jsonCode(@NotNull final SlingHttpServletResponse response,
                            @NotNull final Resource targetResource, int depth, boolean source)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/display/code.html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                try (final StringWriter content = new StringWriter();
                     final JsonWriter jsonWriter = new JsonWriter(content)) {
                    jsonWriter.setIndent(this.indent);
                    dumpJson(jsonWriter, targetResource, 0, depth, resourceFilter,
                            source ? this::isAllowedProperty : resourceFilter::isAllowedProperty,
                            source ? this::isAllowedMixin : null, null);
                    final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                            Collections.singletonMap("content", content.toString()));
                    prepareTextResponse(response, null);
                    IOUtils.copy(reader, writer);
                }
            }
        }
    }

    @Override
    public void dumpJson(@NotNull final JsonWriter writer,
                         @NotNull final Resource resource, int depth, @Nullable Integer maxDepth,
                         @NotNull final ResourceFilter resourceFilter,
                         @NotNull final Function<String, Boolean> propertyFilter,
                         @Nullable final Function<String, Boolean> mixinFilter,
                         @Nullable final Function<Object, Object> transformer)
            throws IOException {
        writer.beginObject();
        dumpJsonContent(writer, resource, depth, maxDepth,
                resourceFilter, propertyFilter, mixinFilter, transformer);
        writer.endObject();
    }

    @Override
    public void dumpJsonContent(@NotNull final JsonWriter writer,
                                @NotNull final Resource resource, int depth, @Nullable Integer maxDepth,
                                @NotNull final ResourceFilter resourceFilter,
                                @NotNull final Function<String, Boolean> propertyFilter,
                                @Nullable final Function<String, Boolean> mixinFilter,
                                @Nullable final Function<Object, Object> transformer)
            throws IOException {
        if (sourceMode && isTranslationsRootFolder(resource)) {
            dumpTranslationsContent(writer, resource);
        } else {
            jsonProperties(writer, resource, propertyFilter, mixinFilter, transformer);
            final String name = resource.getName();
            if (JCR_CONTENT.equals(name) || resource.getPath().contains("/" + JCR_CONTENT + "/")) {
                maxDepth = null;
            }
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && resourceFilter.isAllowedResource(content)) {
                writer.name(content.getName());
                dumpJson(writer, content, depth + 1, maxDepth,
                        resourceFilter, propertyFilter, mixinFilter, transformer);
            }
            if (maxDepth == null || depth + 1 < maxDepth) {
                for (final Resource child : resource.getChildren()) {
                    if (resourceFilter.isAllowedResource(child)) {
                        final String childName = child.getName();
                        if (!JCR_CONTENT.equals(childName)) {
                            writer.name(childName);
                            dumpJson(writer, child, depth + 1, maxDepth,
                                    resourceFilter, propertyFilter, mixinFilter, transformer);
                        }
                    }
                }
            }
        }
    }

    protected void jsonProperties(@NotNull final JsonWriter writer, @NotNull final Resource resource,
                                  @NotNull final Function<String, Boolean> propertyFilter,
                                  @Nullable final Function<String, Boolean> mixinFilter,
                                  @Nullable final Function<Object, Object> transformer)
            throws IOException {
        final Map<String, Object> properties = new TreeMap<>(PROPERTY_NAME_COMPARATOR);
        for (final Map.Entry<String, Object> property : resource.getValueMap().entrySet()) {
            final String name = property.getKey();
            if (propertyFilter.apply(name)) {
                final Object value = property.getValue();
                if (JCR_PRIMARY_TYPE.equals(name)) {
                    writer.name(name);
                    Properties.toJson(writer, value, JSON_DATE_FORMAT);
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
            writer.name(property.getKey());
            Properties.toJson(writer, property.getValue(), JSON_DATE_FORMAT);
        }
    }

    protected void dumpTranslationsFolder(@NotNull final JsonWriter writer, @NotNull final Resource folder)
            throws IOException {
        writer.beginObject();
        dumpTranslationsContent(writer, folder);
        writer.endObject();
    }

    protected void dumpTranslationsContent(@NotNull final JsonWriter writer, @NotNull final Resource folder)
            throws IOException {
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
        for (final String key : entryKeys) {
            final Resource item = folder.getChild(key);
            if (item != null) {
                final ValueMap values = item.getValueMap();
                writer.name(values.get("sling:key", key))
                        .value(values.get("sling:message", ""));
            }
        }
        for (final String key : folderKeys) {
            final Resource item = folder.getChild(key);
            if (item != null) {
                writer.name(item.getName());
                dumpTranslationsFolder(writer, item);
            }
        }
    }
}
