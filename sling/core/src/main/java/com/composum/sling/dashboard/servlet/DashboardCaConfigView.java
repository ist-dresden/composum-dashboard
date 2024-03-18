package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.ResourceFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.caconfig.ConfigurationBuilder;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

    /** Property keys we do not display from the configurations. */
    public static final Pattern IGNORED_PROPERTY_KEYS = Pattern.compile("^jcr:primaryType$");


    @ObjectClassDefinition(name = "Composum Dashboard CA Config View")
    public @interface Config {

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAME_NAME, description = ConfigurationConstants.CFG_NAME_DESCRIPTION)
        String name() default "caconfig";

        @AttributeDefinition(name = ConfigurationConstants.CFG_CONTEXT_NAME,
                description = ConfigurationConstants.CFG_CONTEXT_DESCRIPTION)
        String[] context() default {
                BROWSER_CONTEXT
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_CATEGORY_NAME,
                description = ConfigurationConstants.CFG_CATEGORY_DESCRIPTION)
        String[] category();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RANK_NAME, description = ConfigurationConstants.CFG_RANK_DESCRIPTION)
        int rank() default 1500;

        @AttributeDefinition(name = ConfigurationConstants.CFG_LABEL_NAME, description = ConfigurationConstants.CFG_LABEL_DESCRIPTION)
        String label() default "CAC";

        @AttributeDefinition(name = ConfigurationConstants.CFG_NAVIGATION_NAME)
        String navTitle();

        @AttributeDefinition(name = "Inspected Configurations",
                description = "A set of templates matching: 'caconfig-type[config-properties,...]' if only some properties should be shown, " +
                        "or 'caconfig-type' if all properties should be shown. caconfig-type is the fully qualified class name of the configuration type.")
        String[] inspectedConfigurations();

        @AttributeDefinition(name = "Inspected Configuration Collections",
                description = "A set of templates matching: 'caconfig-type[config-properties,...]' if only some properties should be shown, " +
                        "or 'caconfig-type' if all properties should be shown. caconfig-type is the fully qualified class name of the configuration type.")
        String[] inspectedConfigurationCollections();

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION)
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/view"
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

    protected transient List<ConfigurationRule> configurations;

    protected transient List<ConfigurationRule> collectionConfigurations;

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        configurations = parseConfigurationRules(config.inspectedConfigurations());
        collectionConfigurations = parseConfigurationRules(config.inspectedConfigurationCollections());
    }

    private List<ConfigurationRule> parseConfigurationRules(String[] configuredRules) {
        List<ConfigurationRule> parsedConfigurations = new ArrayList<>();
        for (final String rule : configuredRules) {
            if (StringUtils.isNotBlank(rule)) {
                Matcher matcher = RULE_PATTERN.matcher(rule);
                if (matcher.matches()) {
                    parsedConfigurations.add(new ConfigurationRule(matcher));
                }
            }
        }
        return parsedConfigurations;
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

        @NotNull
        protected final ConfigurationRule config;
        @NotNull
        protected final ValueMap valueMap;

        public ConfigurationProvider(@NotNull ConfigurationRule config, @NotNull ValueMap valueMap) {
            this.config = config;
            this.valueMap = valueMap;
        }

        @Override
        public String getName() {
            return config.configType;
        }

        @Override
        public String getLabel() {
            return config.configType;
        }

        @Override
        public boolean isAvailable() {
            return !valueMap.isEmpty();
        }

        @Override
        public @NotNull Iterable<String> getPropertyNames() {
            final Set<String> propertyNames = new HashSet<>();
            for (String property : valueMap.keySet()) {
                if (config.properties.isEmpty() && !IGNORED_PROPERTY_KEYS.matcher(property).matches()) {
                    propertyNames.add(property);
                }
                for (final Pattern pattern : config.properties) {
                    final Matcher matcher = pattern.matcher(property);
                    if (matcher.matches()) {
                        propertyNames.add(property);
                        break;
                    }
                }
            }
            return propertyNames;
        }

        @Override
        public @Nullable Object getProperty(@NotNull final String name) {
            return valueMap.get(name);
        }
    }

    @Override
    protected @NotNull List<SettingsProvider> getSettingsProviders(@NotNull final SlingHttpServletRequest request) {
        final List<SettingsProvider> providers = new ArrayList<>();
        final Resource targetResource = dashboardManager.getRequestResource(request);
        if (targetResource != null) {
            final ConfigurationBuilder builder = targetResource.adaptTo(ConfigurationBuilder.class);
            if (builder != null) {
                for (ConfigurationRule config : configurations) {
                    @NotNull ValueMap valueMap = builder.name(config.configType).asValueMap();
                    providers.add(new ConfigurationProvider(config, valueMap));
                }
                for (ConfigurationRule config : collectionConfigurations) {
                    ConfigurationBuilder builderForConfig = builder.name(config.configType);
                    @NotNull Collection<ValueMap> valueMaps = builderForConfig.asValueMapCollection();
                    for (@NotNull ValueMap valueMap : valueMaps) {
                        providers.add(new ConfigurationProvider(config, valueMap));
                    }
                    if (valueMaps.isEmpty()) {
                        providers.add(new ConfigurationProvider(config,
                                new ValueMapDecorator(Collections.emptyMap())));
                    }
                }
            }
        }
        return providers;
    }
}
