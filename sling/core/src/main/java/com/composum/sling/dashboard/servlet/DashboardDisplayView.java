package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.DashboardRequest;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
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
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.composum.sling.dashboard.DashboardConfig.JCR_CONTENT;
import static com.composum.sling.dashboard.DashboardConfig.JCR_DATA;
import static com.composum.sling.dashboard.DashboardConfig.JCR_MIME_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.SLING_RESOURCE_TYPE;
import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardDisplayView.Config.class)
public class DashboardDisplayView extends AbstractWidgetServlet implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/display";

    @ObjectClassDefinition(name = "Composum Dashboard Display View")
    public @interface Config {

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "display";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 1000;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "Preview";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = "Document Loading")
        boolean loadDocuments() default true;

        @AttributeDefinition(name = "Parameter Fields",
                description = "the set of form fields to add content URL parameters")
        String[] parameterFields();

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
                "html"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_PATHS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_PATHS_DESCRIPTION)
        String[] sling_servlet_paths();
    }

    protected static final String OPTION_LOAD = "load";

    protected static final List<String> HTML_MODES =
            Arrays.asList(OPTION_VIEW, OPTION_FORM, OPTION_LOAD);

    enum Type {PREVIEW, TEXT, CODE, IMAGE, VIDEO, DOCUMENT, BINARY, UNKNOWN}

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected ResourceFilter resourceFilter;

    protected boolean loadDocuments = true;

    protected List<String> parameterFields;

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        loadDocuments = config.loadDocuments();
        parameterFields = List.of(config.parameterFields());
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void embedScripts(@NotNull final ResourceResolver resolver,
                             @NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest slingRequest,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        try (DashboardRequest request = new DashboardRequest(slingRequest)) {
            switch (getHtmlMode(request, HTML_MODES)) {
                case OPTION_VIEW:
                default:
                    final Resource resource = resourceFilter.getRequestResource(request);
                    if (resource != null) {
                        final Resource target = getTargetResource(resource);
                        final Type displayType = getDisplayType(resource);
                        switch (displayType) {
                            case PREVIEW:
                                preview(request, response, displayType, Collections.singletonMap("targetUrl",
                                        getTargetUrl(resource, "html") + getRequestParameters(request, true)));
                                break;
                            case DOCUMENT:
                            default:
                                preview(request, response, displayType, Collections.singletonMap("targetUrl",
                                        (loadDocuments ? getWidgetUri(request, DEFAULT_RESOURCE_TYPE, HTML_MODES, OPTION_LOAD) : "")
                                                + getTargetUrl(resource, null)));
                                break;
                            case IMAGE:
                            case VIDEO:
                            case BINARY:
                                preview(request, response, displayType, new HashMap<>() {{
                                    put("targetUrl", getTargetUrl(resource, null));
                                    put("targetType", getExtension(target));
                                    put("filename", target.getName());
                                }});
                                break;
                            case TEXT:
                            case CODE:
                                preview(request, response, displayType, new HashMap<>() {{
                                    put("targetUrl", getTargetUrl(resource, null));
                                    put("targetType", getExtension(target));
                                    put("filename", target.getName());
                                    put("content", getContent(resource));
                                }});
                                break;
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                    break;
                case OPTION_FORM:
                    sendFormFields(response, parameterFields);
                    break;
                case OPTION_LOAD:
                    loadContent(request, response);
                    break;
            }
        }
    }

    protected void preview(@NotNull final SlingHttpServletRequest request,
                           @NotNull final SlingHttpServletResponse response,
                           @NotNull final Type template, @NotNull final Map<String, Object> properties)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/display/"
                        + template.name().toLowerCase() + ".html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            final Writer writer = new ValueEmbeddingWriter(response.getWriter(), properties, Locale.ENGLISH, this.getClass());
            if (reader != null) {
                prepareTextResponse(response, null);
                IOUtils.copy(reader, writer);
            }
        }
    }


    protected @NotNull String getTargetUrl(@NotNull Resource resource, @Nullable String extension) {
        resource = getTargetResource(resource);
        StringBuilder result = new StringBuilder();
        result.append(resource.getPath());
        if (StringUtils.isNotBlank(extension)) {
            result.append('.').append(extension);
        }
        return result.toString();
    }

    protected @NotNull Resource getTargetResource(@NotNull Resource resource) {
        if (JCR_CONTENT.equals(resource.getName())) {
            resource = Optional.ofNullable(resource.getParent()).orElse(resource);
        }
        return resource;
    }

    protected @Nullable String getContent(@NotNull Resource resource) {
        resource = getContentResource(resource);
        try (InputStream content = resource.getValueMap().get(JCR_DATA, InputStream.class)) {
            return content != null ? IOUtils.toString(content, StandardCharsets.UTF_8) : "";
        } catch (IOException ex) {
            return "";
        }
    }

    protected @NotNull Resource getContentResource(@NotNull final Resource resource) {
        Resource content = resource;
        final String primaryType = resource.getValueMap().get(JCR_PRIMARY_TYPE, "");
        switch (primaryType) {
            case "nt:file":
                content = resource.getChild(JCR_CONTENT);
                break;
            case "dam:Asset":
                content = resource.getChild(JCR_CONTENT + "/renditions/original/" + JCR_CONTENT);
                break;
            case "dam:AssetContent":
                content = resource.getChild("renditions/original/" + JCR_CONTENT);
                break;
        }
        return content != null ? content : resource;
    }

    protected @NotNull Type getDisplayType(@NotNull final Resource resource) {
        final String primaryType = resource.getValueMap().get(JCR_PRIMARY_TYPE, "");

        if (FILE_TYPES.contains(primaryType)) {
            final String mimeType = getMimeType(resource);
            if (StringUtils.isNotBlank(mimeType)) {
                for (String pattern : new String[]{
                        mimeType,
                        StringUtils.substringBefore(mimeType, "/"),
                        StringUtils.substringAfter(mimeType, "/")
                }) {
                    if (!"text".equals(pattern)) {
                        Type type = TYPETABLE.get(pattern);
                        if (type != null) {
                            return type;
                        }
                    }
                }
            }
            final String extension = getExtension(resource);
            if (StringUtils.isNotBlank(extension)) {
                Type type = TYPETABLE.get(extension);
                if (type != null) {
                    return type;
                }
            }
            return Type.BINARY;
        }

        String resourceType = getResourceType(resource);
        if (StringUtils.isNotBlank(resourceType)) {
            return Type.PREVIEW;
        }

        return Type.UNKNOWN;
    }

    protected @Nullable String getResourceType(@NotNull Resource resource) {
        final ValueMap values = resource.getValueMap();
        String resourceType = values.get(SLING_RESOURCE_TYPE, String.class);
        if (StringUtils.isBlank(resourceType) && !JCR_CONTENT.equals(resource.getName())) {
            final Resource content = resource.getChild(JCR_CONTENT);
            if (content != null) {
                resourceType = content.getValueMap().get(SLING_RESOURCE_TYPE, String.class);
            }
        }
        return resourceType;
    }

    protected @Nullable String getMimeType(@NotNull Resource resource) {
        String mimeType = resource.getValueMap().get(JCR_MIME_TYPE, String.class);
        if (StringUtils.isBlank(mimeType)) {
            resource = getContentResource(resource);
            mimeType = resource.getValueMap().get(JCR_MIME_TYPE, String.class);
        }
        return mimeType;
    }

    protected @Nullable String getExtension(@NotNull Resource resource) {
        if (JCR_CONTENT.equals(resource.getName())) {
            resource = resource.getParent();
        }
        return resource != null ? StringUtils.substringAfterLast(resource.getName(), ".") : null;
    }

    protected static final List<String> FILE_TYPES = Arrays.asList(
            "nt:file",
            "nt:resource",
            "oak:Resource",
            "dam:Asset",
            "dam:AssetContent"
    );

    protected static final Map<String, Type> TYPETABLE = new HashMap<>() {{
        put("application/pdf", Type.DOCUMENT);
        put("pdf", Type.DOCUMENT);
        put("application/json", Type.DOCUMENT);
        put("json", Type.DOCUMENT);
        put("text/html", Type.CODE);
        put("html", Type.CODE);
        put("htm", Type.CODE);
        put("xhtml", Type.CODE);
        put("text/xml", Type.CODE);
        put("xml", Type.CODE);
        put("text", Type.TEXT);
        put("txt", Type.TEXT);
        put("csv", Type.TEXT);
        put("tsv", Type.TEXT);
        put("log", Type.TEXT);
        put("image", Type.IMAGE);
        put("avif", Type.IMAGE);
        put("webp", Type.IMAGE);
        put("png", Type.IMAGE);
        put("jpg", Type.IMAGE);
        put("jpeg", Type.IMAGE);
        put("tiff", Type.IMAGE);
        put("tif", Type.IMAGE);
        put("heic", Type.IMAGE);
        put("gif", Type.IMAGE);
        put("svg", Type.IMAGE);
        put("xml+svg", Type.IMAGE);
        put("video", Type.VIDEO);
        put("webm", Type.VIDEO);
        put("mp4", Type.VIDEO);
        put("m4v", Type.VIDEO);
        put("mov", Type.VIDEO);
        put("mpg", Type.VIDEO);
        put("mpeg", Type.VIDEO);
        put("mkv", Type.VIDEO);
        put("wmv", Type.VIDEO);
        put("avi", Type.VIDEO);
        put("c", Type.CODE);
        put("cc", Type.CODE);
        put("cfg", Type.CODE);
        put("clj", Type.CODE);
        put("conf", Type.CODE);
        put("config", Type.CODE);
        put("cpp", Type.CODE);
        put("cs", Type.CODE);
        put("css", Type.CODE);
        put("d", Type.CODE);
        put("dart", Type.CODE);
        put("diff", Type.CODE);
        put("e", Type.CODE);
        put("ecma", Type.CODE);
        put("esp", Type.CODE);
        put("ftl", Type.CODE);
        put("groovy", Type.CODE);
        put("gvy", Type.CODE);
        put("h", Type.CODE);
        put("handlebars", Type.CODE);
        put("hbs", Type.CODE);
        put("hh", Type.CODE);
        put("java", Type.CODE);
        put("javascript", Type.CODE);
        put("js", Type.CODE);
        put("jsf", Type.CODE);
        put("jsp", Type.CODE);
        put("jspf", Type.CODE);
        put("jspx", Type.CODE);
        put("kt", Type.CODE);
        put("less", Type.CODE);
        put("m", Type.CODE);
        put("markdown", Type.CODE);
        put("md", Type.CODE);
        put("mm", Type.CODE);
        put("patch", Type.CODE);
        put("php", Type.CODE);
        put("pl", Type.CODE);
        put("properties", Type.CODE);
        put("py", Type.CODE);
        put("rb", Type.CODE);
        put("rs", Type.CODE);
        put("ru", Type.CODE);
        put("ruby", Type.CODE);
        put("sass", Type.CODE);
        put("scala", Type.CODE);
        put("scss", Type.CODE);
        put("sh", Type.CODE);
        put("sql", Type.CODE);
    }};

    protected void loadContent(@NotNull final SlingHttpServletRequest request,
                               @NotNull final SlingHttpServletResponse response)
            throws IOException {
        Resource resource = resourceFilter.getRequestResource(request);
        if (resource != null) {
            String filename = null;
            String primaryType = resource.getValueMap().get(JCR_PRIMARY_TYPE, "");
            switch (primaryType) {
                case "nt:file":
                    filename = resource.getName();
                    resource = resource.getChild(JCR_CONTENT);
                    break;
                case "dam:Asset":
                    filename = resource.getName();
                    resource = resource.getChild(JCR_CONTENT + "/renditions/original/" + JCR_CONTENT);
                    break;
                case "dam:AssetContent":
                    filename = Optional.ofNullable(resource.getParent()).orElse(resource).getName();
                    resource = resource.getChild("renditions/original/" + JCR_CONTENT);
                    break;
            }
            if (resource != null) {
                ValueMap values = resource.getValueMap();
                try (InputStream stream = values.get(JCR_DATA, InputStream.class)) {
                    if (stream != null) {
                        String mimeType = values.get(JCR_MIME_TYPE, String.class);
                        if (StringUtils.isNotBlank(mimeType)) {
                            response.setContentType(mimeType);
                        }
                        String disposition = "inline";
                        if (StringUtils.isNotBlank(filename)) {
                            disposition += "; filename=" + filename;
                        }
                        response.setHeader("Content-Disposition", disposition);
                        Calendar lastModified = values.get("jcr:lastModified", Calendar.class);
                        if (lastModified != null) {
                            response.setDateHeader(HttpConstants.HEADER_LAST_MODIFIED, lastModified.getTimeInMillis());
                        }
                        IOUtils.copy(stream, response.getOutputStream());
                        return;
                    }
                }
            }
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
