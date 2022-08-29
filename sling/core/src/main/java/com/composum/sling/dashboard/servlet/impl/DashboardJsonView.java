package com.composum.sling.dashboard.servlet.impl;

import com.composum.sling.dashboard.service.DashboardBrowser;
import com.composum.sling.dashboard.servlet.AbstractWidgetServlet;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Constants;
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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.impl.DashboardBrowserServlet.BROWSER_CONTEXT;
import static com.composum.sling.dashboard.servlet.impl.DashboardBrowserServlet.JCR_CONTENT;
import static com.composum.sling.dashboard.servlet.impl.DashboardBrowserServlet.JCR_MIXIN_TYPES;
import static com.composum.sling.dashboard.servlet.impl.DashboardBrowserServlet.JCR_PRIMARY_TYPE;

@Component(service = {Servlet.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Dashboard Json Source View",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = DashboardJsonView.Config.class)
public class DashboardJsonView extends AbstractWidgetServlet {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/components/source/json";


    @ObjectClassDefinition(name = "Composum Dashboard Json Source View")
    public @interface Config {

        @AttributeDefinition(name = "Context")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String category();

        @AttributeDefinition(name = "Rank")
        int rank() default 2000;

        @AttributeDefinition(name = "Label")
        String label() default "JSON";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Allowed Property Patterns")
        String[] allowedPropertyPatterns() default {
                "^.*$"
        };

        @AttributeDefinition(name = "Disabled Property Patterns")
        String[] disabledPropertyPatterns() default {
                "^rep:.*$",
                "^.*password.*$"
        };

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
    protected DashboardBrowser browser;

    protected List<Pattern> allowedPropertyPatterns;
    protected List<Pattern> disabledPropertyPatterns;

    protected int maxDepth = 1;
    protected boolean sourceMode = true;

    @Activate
    @Modified
    protected void activate(DashboardJsonView.Config config) {
        super.activate(config.context(), config.category(), config.rank(), config.label(), config.navTitle(),
                config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        allowedPropertyPatterns = patternList(config.allowedPropertyPatterns());
        disabledPropertyPatterns = patternList(config.disabledPropertyPatterns());
        maxDepth = config.maxDepth();
        sourceMode = config.sourceMode();
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    protected boolean isAllowedProperty(@NotNull final String name) {
        for (Pattern allowed : allowedPropertyPatterns) {
            if (allowed.matcher(name).matches()) {
                for (Pattern disabled : disabledPropertyPatterns) {
                    if (disabled.matcher(name).matches()) {
                        return false;
                    }
                }
                if (sourceMode) {
                    for (Pattern disabled : NON_SOURCE_PROPS) {
                        if (disabled.matcher(name).matches()) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final Resource targetResource = browser.getRequestResource(request);
        if (targetResource != null) {
            final String mode = getHtmlMode(request, HTML_MODES);
            if (OPTION_LOAD.equals(mode) || "json".equals(pathInfo.getExtension())) {
                response.setContentType("application/json;charset=UTF-8");
                final JsonWriter writer = new JsonWriter(response.getWriter());
                writer.setIndent("  ");
                dumpJson(writer, targetResource, 0);
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
                response.setContentType("text/html;charset=UTF-8");
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
                    dumpJson(jsonWriter, targetResource, 0);
                    final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                            Collections.singletonMap("content", content.toString()));
                    response.setContentType("text/html;charset=UTF-8");
                    IOUtils.copy(reader, writer);
                }
            }
        }
    }

    protected void dumpJson(@NotNull final JsonWriter writer, @NotNull final Resource resource,
                            @Nullable Integer depth)
            throws IOException {
        writer.beginObject();
        jsonProperties(writer, resource);
        final String name = resource.getName();
        if (JCR_CONTENT.equals(name)) {
            depth = null;
        } else {
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null && browser.isAllowedResource(content)) {
                writer.name(content.getName());
                dumpJson(writer, content, null);
            }
        }
        if (depth == null || depth < maxDepth) {
            for (final Resource child : resource.getChildren()) {
                if (browser.isAllowedResource(child)) {
                    final String childName = child.getName();
                    if (!JCR_CONTENT.equals(childName)) {
                        writer.name(childName);
                        dumpJson(writer, child, depth != null ? depth + 1 : null);
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
                    jsonProperty(writer, property.getValue());
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
            jsonProperty(writer, property.getValue());
        }
    }
}