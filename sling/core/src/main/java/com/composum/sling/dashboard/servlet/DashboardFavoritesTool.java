package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.util.DashboardRequest;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardFavoritesTool.Config.class)
public class DashboardFavoritesTool extends AbstractWidgetServlet implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/favorites";

    @ObjectClassDefinition(name = "Composum Dashboard Favorites Tool")
    public @interface Config {

        @AttributeDefinition(name = "Groups")
        String[] favoriteGroups() default {
                "Content=^/content(/.*)$"
        };

        @AttributeDefinition(name = "max History Items")
        int historyMax() default 100;

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "favorites";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                DashboardBrowserServlet.BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category() default {"favorites", "tool"};

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 4500;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "Favorites";

        @AttributeDefinition(name = "Icon")
        String icon() default "star-o";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION)
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view"
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

    public static final List<String> HTML_MODES = List.of(OPTION_VIEW);

    public static final Pattern GROUP_PATTERN = Pattern.compile("^(?<label>[^=]+)=(?<pattern>.+)$");

    public static final String TEMPLATE_BASE = "/com/composum/sling/dashboard/plugin/favorites/";

    @Reference
    protected XSSAPI xssapi;

    protected Map<String, String> favoriteGroups = new LinkedHashMap<>();
    protected int historyMax;
    protected ValueMap properties = new ValueMapDecorator(new HashMap<>());

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        this.favoriteGroups.clear();
        this.favoriteGroups.put("All", "^.*$");
        for (final String rule : config.favoriteGroups()) {
            final Matcher matcher = GROUP_PATTERN.matcher(rule);
            if (matcher.matches()) {
                this.favoriteGroups.put(matcher.group("label"), matcher.group("pattern"));
            }
        }
        this.historyMax = config.historyMax();
        this.properties.put("icon", config.icon());
    }

    @Override
    public <T> @NotNull T getProperty(@NotNull String name, @NotNull T defaultValue) {
        return properties.get(name, super.getProperty(name, defaultValue));
    }

    @Override
    public void embedScripts(@NotNull final ResourceResolver resolver,
                             @NotNull final PrintWriter writer, @NotNull final String mode)
            throws IOException {
        if (OPTION_PAGE.equals(mode) || OPTION_VIEW.equals(mode)) {
            writer.append("<script>\n");
            copyResource(this.getClass(), TEMPLATE_BASE + "script.js", writer);
            writer.append("</script>\n");
        }
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
            if (mode.equals(OPTION_VIEW)) {
                prepareTextResponse(response, null);
                final PrintWriter writer = response.getWriter();
                htmlView(writer);
            }
        }
    }

    protected void htmlView(@NotNull final PrintWriter writer)
            throws IOException {
        writer.append("<style>\n");
        copyResource(this.getClass(), TEMPLATE_BASE + "style.css", writer);
        writer.append("</style>\n");
        writer.append("<div class=\"dashboard-widget__favorites\" data-history-max=\"")
                .append(String.valueOf(historyMax)).append("\">");
        writer.append("<div><ul class=\"dashboard-widget__favorites-groups nav nav-tabs\" role=\"tablist\">\n");
        for (final Map.Entry<String, String> entry : favoriteGroups.entrySet()) {
            writeTab(writer, "favorites-group", entry.getKey(), entry.getKey(), entry.getValue());
        }
        writeTab(writer, "favorites-history fa fa-history", "history", "", "");
        writer.append("</ul>");
        writer.append("<div class=\"dashboard-widget__favorites-clear fa fa-trash\"></div>\n");
        writer.append("</div>\n");
        writer.append("<div class=\"dashboard-widget__favorites-list\">"
                + "<table class=\"table table-sm table-striped\">"
                + "<tbody class=\"dashboard-widget__favorites-content\"></tbody>"
                + "</table></div>\n");
        writer.append("</div>");
    }

    protected void writeTab(@NotNull final PrintWriter writer, @NotNull final String linkCssClasses,
                            @NotNull String id, @NotNull String label, @NotNull String pattern) {
        id = xssapi.encodeForHTMLAttr(id.replaceAll("[\\s]+", "").toLowerCase());
        label = xssapi.encodeForHTMLAttr(label);
        writer.append("<li class=\"nav-item favorite-group-").append(id)
                .append("\"><a class=\"nav-link ").append(linkCssClasses)
                .append("\" id=\"").append(id);
        if ("history".equals(id)) {
            writer.append("\" title=\"").append("History");
        }
        writer.append("\" data-toggle=\"tab\" href=\"#").append(label)
                .append("\" role=\"tab\" data-pattern=\"").append(xssapi.encodeForHTMLAttr(pattern))
                .append("\" aria-selected=\"false\">").append(label)
                .append("</a></li>\n");
    }
}
