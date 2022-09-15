package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.JsonRenderer;
import com.composum.sling.dashboard.service.ResourceFilter;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = {Servlet.class, DashboardWidget.class, JsonRenderer.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardJsonView.Config.class)
public class DashboardJsonView extends AbstractWidgetServlet implements JsonRenderer, ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/source/json";


    @ObjectClassDefinition(name = "Composum Dashboard Json Source View")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "json";

        @AttributeDefinition(name = "Context")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category();

        @AttributeDefinition(name = "Rank")
        int rank() default 2000;

        @AttributeDefinition(name = "Label")
        String label() default "JSON";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Max Depth")
        int maxDepth() default 1;

        @AttributeDefinition(name = "Source Mode")
        boolean sourceMode() default true;

        @AttributeDefinition(name = "Servlet Types",
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
                "json"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servletd paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    public static final String OPTION_LOAD = "load";

    public static final List<String> HTML_MODES = Arrays.asList(OPTION_VIEW, OPTION_LOAD);

    public static final List<Pattern> NON_SOURCE_PROPS = Arrays.asList(
            Pattern.compile("^jcr:(uuid|data)$"),
            Pattern.compile("^jcr:(baseVersion|predecessors|versionHistory|isCheckedOut)$"),
            Pattern.compile("^jcr:(created|lastModified).*$"),
            Pattern.compile("^cq:last(Modified|Replicat).*$")
    );

    public static final List<Pattern> NON_SOURCE_MIXINS = List.of(
            Pattern.compile("^rep:AccessControllable$")
    );

    @Reference
    protected ResourceFilter resourceFilter;

    protected int maxDepth = 1;
    protected boolean sourceMode = true;

    @Activate
    @Modified
    protected void activate(Config config) {
        super.activate(config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        maxDepth = config.maxDepth();
        sourceMode = config.sourceMode();
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final Resource targetResource = resourceFilter.getRequestResource(request);
        if (targetResource != null) {
            final String mode = getHtmlMode(request, HTML_MODES);
            if (OPTION_LOAD.equals(mode) || "json".equals(pathInfo.getExtension())) {
                response.setContentType("application/json;charset=UTF-8");
                final JsonWriter writer = new JsonWriter(response.getWriter());
                writer.setIndent("  ");
                if (!dumpTranslations(writer, targetResource)) {
                    dumpJson(writer, targetResource, 0, maxDepth);
                }
            } else {
                final String widgetUri = getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_LOAD);
                if (StringUtils.isNotBlank(widgetUri)) {
                    jsonPreview(request, response, targetResource);
                } else {
                    jsonCode(request, response, targetResource);
                }
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    protected void jsonPreview(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response,
                               @NotNull final Resource targetResource)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/view/display/preview.html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                        Collections.singletonMap("targetUrl",
                                getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_LOAD)
                                        + targetResource.getPath()), Locale.ENGLISH, this.getClass());
                prepareHtmlResponse(response);
                IOUtils.copy(reader, writer);
            }
        }
    }

    protected void jsonCode(@NotNull final SlingHttpServletRequest request,
                            @NotNull final SlingHttpServletResponse response,
                            @NotNull final Resource targetResource)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/view/display/code.html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                try (final StringWriter content = new StringWriter();
                     final JsonWriter jsonWriter = new JsonWriter(content)) {
                    jsonWriter.setIndent("  ");
                    dumpJson(jsonWriter, targetResource, 0, maxDepth);
                    final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                            Collections.singletonMap("content", content.toString()));
                    prepareHtmlResponse(response);
                    IOUtils.copy(reader, writer);
                }
            }
        }
    }

    protected boolean dumpTranslations(@NotNull final JsonWriter writer, @NotNull final Resource resource)
            throws IOException {
        final ValueMap values = resource.getValueMap();
        if (!NT_FILE.equals(values.get(JCR_PRIMARY_TYPE, String.class))
                && Arrays.asList(values.get(JCR_MIXIN_TYPES, new String[0])).contains("mix:language")) {
            dumpTranslationFolder(writer, resource);
            return true;
        }
        return false;
    }

    protected void dumpTranslationFolder(@NotNull final JsonWriter writer, @NotNull final Resource folder)
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
        writer.beginObject();
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
                dumpTranslationFolder(writer, item);
            }
        }
        writer.endObject();
    }

    @Override
    public void dumpJson(@NotNull final JsonWriter writer, @NotNull final Resource resource,
                         int depth, @Nullable Integer maxDepth)
            throws IOException {
        writer.beginObject();
        jsonProperties(writer, resource);
        final String name = resource.getName();
        if (JCR_CONTENT.equals(name) || resource.getPath().contains("/" + JCR_CONTENT + "/")) {
            maxDepth = null;
        } else {
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && resourceFilter.isAllowedResource(content)) {
                writer.name(content.getName());
                dumpJson(writer, content, depth + 1, maxDepth);
            }
        }
        if (maxDepth == null || depth < maxDepth) {
            for (final Resource child : resource.getChildren()) {
                if (resourceFilter.isAllowedResource(child)) {
                    final String childName = child.getName();
                    if (!JCR_CONTENT.equals(childName)) {
                        writer.name(childName);
                        dumpJson(writer, child, depth + 1, maxDepth);
                    }
                }
            }
        }
        writer.endObject();
    }

    protected void jsonProperties(@NotNull final JsonWriter writer, @NotNull final Resource resource)
            throws IOException {
        final Map<String, Object> properties = new TreeMap<>();
        for (final Map.Entry<String, Object> property : resource.getValueMap().entrySet()) {
            final String name = property.getKey();
            if (isAllowedProperty(name)) {
                if (JCR_PRIMARY_TYPE.equals(name)) {
                    writer.name(name);
                    Properties.toJson(writer, property.getValue(), JSON_DATE_FORMAT);
                } else {
                    if (sourceMode && JCR_MIXIN_TYPES.equals(name)) {
                        final Object values = filterValues(property.getValue(), NON_SOURCE_MIXINS);
                        if (values instanceof String[] && ((String[]) values).length > 0) {
                            properties.put(name, values);
                        }
                    } else {
                        properties.put(name, property.getValue());
                    }
                }
            }
        }
        for (final Map.Entry<String, Object> property : properties.entrySet()) {
            writer.name(property.getKey());
            Properties.toJson(writer, property.getValue(), JSON_DATE_FORMAT);
        }
    }

    protected boolean isAllowedProperty(@NotNull final String name) {
        if (resourceFilter.isAllowedProperty(name)) {
            if (sourceMode) {
                for (Pattern disabled : NON_SOURCE_PROPS) {
                    if (disabled.matcher(name).matches()) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }
}