package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.ResourceFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.apache.sling.caconfig.ConfigurationResolveException;
import org.apache.sling.caconfig.resource.ConfigurationResourceResolver;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.DashboardBrowserServlet.BROWSER_CONTEXT;

/**
 * a primitive viewer for the settings of a configured set of services
 */
@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardCaConfigView.Config.class)
public class DashboardCaConfigView extends AbstractSettingsWidget implements ContentGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardCaConfigView.class);

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/caconfig";

    @ObjectClassDefinition(name = "Composum Dashboard CA Config View")
    public @interface Config {

        @AttributeDefinition(name = "Name", description = "An ID for the widget.")
        String name() default "caconfig";

        @AttributeDefinition(name = "Context",
                description = "The context where the widget is available - e.g. 'browser' or 'dashboard'. " +
                        "Relevant only when the dashboard is configured using servlet paths.")
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = "Category",
                description = "The category of a widget - in the browser e.g. 'favorites', 'tool', 'search'. " +
                        "Relevant only when the dashboard is configured using servlet paths.")
        String[] category();

        @AttributeDefinition(name = "Rank", description = "The rank is used for ordering widgets / views. " +
                "Relevant only when the dashboard is configured using servlet paths.")
        int rank() default 1500;

        @AttributeDefinition(name = "Label", description = "The human readable widget label.")
        String label() default "CAC";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Inspected Configurations",
                description = "A set of templates matching: 'caconfig-type[config-properties,...]' if only some properties should be shown, " +
                        "or 'caconfig-type' if all properties should be shown. caconfig-type is the fully qualified class name of the configuration type.")
        String[] inspectedConfigurations();

        @AttributeDefinition(name = "Resource Types",
                description = "The resource types implemented by this servlet." +
                        "Relevant only when the it is rendered using a content page.")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view"
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "The possible extensions supported by this servlet.")
        String[] sling_servlet_extensions() default {
                "html",
                "json"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "The servlet paths if this configuration variant should be supported. " +
                        "Alternatively, the servlet can be rendered from a special content page using it's resource type(s).")
        String[] sling_servlet_paths();
    }

    public static final Pattern RULE_PATTERN = Pattern.compile(
            "^(?<type>[^\\[(]+)(?<filter>\\([^)]+\\))?(\\[(?<props>.*)])?$");

    public static class ConfigurationRule {

        public final String configType;
        public final List<Pattern> properties;

        public ConfigurationRule(Matcher matcher) {
            configType = matcher.group("type");
            properties = new ArrayList<>();
            final String props = matcher.group("props");
            if (StringUtils.isNotBlank(props)) {
                for (String pattern : StringUtils.split(props, ",")) {
                    properties.add(Pattern.compile(pattern));
                }
            }
        }
    }

    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_VIEW, OPTION_JSON);

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected DynamicClassLoaderManager classLoaderManager;

    @Reference
    protected ConfigurationResourceResolver configurationResolver;

    @Reference
    protected DashboardManager dashboardManager;

    protected transient List<ConfigurationRule> configuration;

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        configuration = new ArrayList<>();
        for (final String rule : config.inspectedConfigurations()) {
            if (StringUtils.isNotBlank(rule)) {
                Matcher matcher = RULE_PATTERN.matcher(rule);
                if (matcher.matches()) {
                    configuration.add(new ConfigurationRule(matcher));
                }
            }
        }
    }

    @Override
    protected @NotNull String defaultResourceType() {
        return DEFAULT_RESOURCE_TYPE;
    }

    @Override
    public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode) {
    }

    @Override
    protected @NotNull String getClientlibPath() {
        return "/com/composum/sling/dashboard/plugin/service/caconfig";
    }

    @Override
    protected @NotNull List<String> getHtmlModes() {
        return HTML_MODES;
    }

    @Override
    protected @NotNull ResourceFilter resourceFilter() {
        return dashboardManager;
    }

    @Override
    protected @NotNull XSSAPI xssApi() {
        return xssapi;
    }

    protected class ConfigurationProvider extends SettingsProvider {

        protected final ConfigurationRule config;
        protected final Class<?> configType;
        protected final Object caConfig;

        public ConfigurationProvider(@NotNull final ConfigurationRule config, @NotNull final Class<?> configType,
                                     @Nullable final Object caConfig) {
            this.config = config;
            this.configType = configType;
            this.caConfig = caConfig;
        }

        @Override
        public String getName() {
            return configType.getName();
        }

        @Override
        public String getLabel() {
            return configType.getSimpleName();
        }

        @Override
        public boolean isAvailable() {
            return caConfig != null;
        }

        @Override
        public @NotNull Iterable<String> getPropertyNames() {
            final Set<String> propertyNames = new HashSet<>();
            for (final Method method : configType.getDeclaredMethods()) {
                if (method.getParameterCount() == 0) {
                    String name = method.getName();
                    if (config.properties.isEmpty()) {
                        propertyNames.add(name);
                    } else {
                        for (final Pattern pattern : config.properties) {
                            final Matcher matcher = pattern.matcher(name);
                            if (matcher.matches()) {
                                propertyNames.add(name);
                                break;
                            }
                        }
                    }
                }
            }
            return propertyNames;
        }

        @Override
        public @Nullable Object getProperty(@NotNull final String name) {
            return getProperty(caConfig, name);
        }
    }

    @Override
    protected @NotNull List<SettingsProvider> getSettingsProviders(@NotNull final SlingHttpServletRequest request) {
        final List<SettingsProvider> providers = new ArrayList<>();
        final Resource targetResource = dashboardManager.getRequestResource(request);
        if (targetResource != null) {
            final ConfigurationBuilder builder = targetResource.adaptTo(ConfigurationBuilder.class);
            if (builder != null) {
                for (ConfigurationRule config : configuration) {
                    try {
                        final Class<?> caConfigType = classLoaderManager.getDynamicClassLoader().loadClass(config.configType);
                        if (caConfigType != null) {
                            providers.add(new ConfigurationProvider(config, caConfigType,
                                    builder.has(caConfigType) ? builder.as(caConfigType) : null));
                        }
                    } catch (ClassNotFoundException | ConfigurationResolveException ignore) {
                        LOG.debug("Configuration type not found: {}", config.configType);
                    }
                }
            }
        }
        return providers;
    }
}
