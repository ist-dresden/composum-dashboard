package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.util.ValueEmbeddingWriter;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public abstract class AbstractSourceView extends AbstractWidgetServlet {

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

    public static final Comparator<String> PROPERTY_NAME_COMPARATOR =
            Comparator.comparing(name -> (name.contains(":") ? name : "zzz:" + name));

    protected int maxDepth = 0;
    protected boolean sourceMode = true;

    protected abstract @NotNull ResourceFilter getResourceFilter();

    @Override
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    protected void preview(@NotNull final SlingHttpServletRequest request,
                           @NotNull final SlingHttpServletResponse response,
                           @NotNull final Resource targetResource)
            throws IOException {
        try (final InputStream pageContent = getClass().getClassLoader()
                .getResourceAsStream("/com/composum/sling/dashboard/plugin/display/preview.html");
             final InputStreamReader reader = pageContent != null ? new InputStreamReader(pageContent) : null) {
            if (reader != null) {
                final Writer writer = new ValueEmbeddingWriter(response.getWriter(),
                        Collections.singletonMap("targetUrl",
                                getWidgetUri(request, defaultResourceType(), HTML_MODES, OPTION_LOAD)
                                        + targetResource.getPath()), Locale.ENGLISH, this.getClass());
                prepareHtmlResponse(response);
                IOUtils.copy(reader, writer);
            }
        }
    }

    protected boolean isAllowedProperty(@NotNull final String name) {
        if (getResourceFilter().isAllowedProperty(name)) {
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