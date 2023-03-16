package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.DashboardManager;
import com.composum.sling.dashboard.service.DashboardWidget;
import com.composum.sling.dashboard.service.ContentGenerator;
import com.composum.sling.dashboard.service.ResourceFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.servlet.Servlet;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.servlet.DashboardServlet.DASHBOARD_CONTEXT;

/**
 * a primitive viewer for the settings of a configured set of services
 */
@Component(service = {Servlet.class, DashboardWidget.class, ContentGenerator.class},
        property = {
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardServiceSettingsWidget.Config.class)
public class DashboardServiceSettingsWidget extends AbstractSettingsWidget implements ContentGenerator {

    public static final String DEFAULT_RESOURCE_TYPE = "composum/dashboard/sling/service/settings";

    @ObjectClassDefinition(name = "Composum Dashboard Service Settings Widget")
    public @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "service-settings";

        @AttributeDefinition(name = "Context")
        String[] context() default {
                DASHBOARD_CONTEXT
        };

        @AttributeDefinition(name = "Category")
        String[] category();

        @AttributeDefinition(name = "Rank")
        int rank() default 2000;

        @AttributeDefinition(name = "Label")
        String label() default "Service Settings";

        @AttributeDefinition(name = "Navigation Title")
        String navTitle();

        @AttributeDefinition(name = "Inspected Settings",
                description = "a set of request templates matching: 'service-type(filter)[service-properties,...]'")
        String[] inspectedSettings();

        @AttributeDefinition(name = "Resource Types",
                description = "the resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                DEFAULT_RESOURCE_TYPE,
                DEFAULT_RESOURCE_TYPE + "/page",
                DEFAULT_RESOURCE_TYPE + "/view",
                DEFAULT_RESOURCE_TYPE + "/tile",
                DEFAULT_RESOURCE_TYPE + "/json"
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "html",
                "json"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servlet paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    protected static final List<String> HTML_MODES = Arrays.asList(OPTION_PAGE, OPTION_VIEW, OPTION_TILE, OPTION_JSON);

    public static final Pattern RULE_PATTERN = Pattern.compile(
            "^(?<type>[^\\[(]+)(?<filter>\\([^)]+\\))?(\\[(?<props>.*)])?$");

    public static class SettingsRule {

        public final String serviceType;
        public final String filter;
        public final List<Pattern> properties;

        public SettingsRule(Matcher matcher) {
            serviceType = matcher.group("type");
            filter = matcher.group("filter");
            properties = new ArrayList<>();
            final String props = matcher.group("props");
            if (StringUtils.isNotBlank(props)) {
                for (String pattern : StringUtils.split(props, ",")) {
                    properties.add(Pattern.compile(pattern));
                }
            }
        }
    }

    @Reference
    protected XSSAPI xssapi;

    @Reference
    protected DashboardManager dashboardManager;

    protected transient List<SettingsRule> configuration;

    @Activate
    @Modified
    protected void activate(final BundleContext bundleContext, final Config config) {
        super.activate(bundleContext,
                config.name(), config.context(), config.category(), config.rank(), config.label(),
                config.navTitle(), config.sling_servlet_resourceTypes(), config.sling_servlet_paths());
        configuration = new ArrayList<>();
        for (final String rule : config.inspectedSettings()) {
            if (StringUtils.isNotBlank(rule)) {
                Matcher matcher = RULE_PATTERN.matcher(rule);
                if (matcher.matches()) {
                    configuration.add(new SettingsRule(matcher));
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
    protected @NotNull List<String> getHtmlModes() {
        return HTML_MODES;
    }

    @Override
    protected @NotNull String getClientlibPath() {
        return "/com/composum/sling/dashboard/plugin/service/settings";
    }

    @Override
    protected @NotNull ResourceFilter resourceFilter() {
        return dashboardManager;
    }

    @Override
    protected @NotNull XSSAPI xssApi() {
        return xssapi;
    }

    protected class ReferenceProvider extends SettingsProvider {

        protected final SettingsRule config;
        protected final ServiceReference<?> reference;

        public ReferenceProvider(SettingsRule config, ServiceReference<?> reference) {
            this.config = config;
            this.reference = reference;
        }

        public Object getService() {
            return bundleContext.getService(reference);
        }

        @Override
        public String getName() {
            return config.serviceType;
        }

        @Override
        public String getLabel() {
            return StringUtils.substringAfterLast(getName(), ".");
        }

        @Override
        public boolean isAvailable() {
            return getService() != null;
        }

        @Override
        public @NotNull Iterable<String> getPropertyNames() {
            final Set<String> propertyNames = new HashSet<>();
            for (final String name : reference.getPropertyKeys()) {
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
            Object service = bundleContext.getService(reference);
            if (service != null) {
                for (final Pattern pattern : config.properties) {
                    final String name = pattern.toString();
                    if (PROPERTY_NAME.matcher(name).matches()) {
                        propertyNames.add(name);
                    }
                }
            }
            return propertyNames;
        }

        @Override
        public @Nullable Object getProperty(@NotNull final String name) {
            Object value = reference.getProperty(name);
            if (value == null) {
                value = getProperty(getService(), name);
            }
            return value;
        }
    }

    @Override
    protected @NotNull List<SettingsProvider> getSettingsProviders(@NotNull final SlingHttpServletRequest request) {
        List<SettingsProvider> providers = new ArrayList<>();
        for (SettingsRule config : configuration) {
            for (ServiceReference<?> reference : getServiceReferences(config)) {
                providers.add(new ReferenceProvider(config, reference));
            }
        }
        return providers;
    }

    protected @NotNull List<ServiceReference<?>> getServiceReferences(@NotNull final SettingsRule config) {
        final List<ServiceReference<?>> serviceReferences = new ArrayList<>();
        try {
            ServiceReference<?>[] references = bundleContext.getAllServiceReferences(config.serviceType,
                    StringUtils.isNotBlank(config.filter) ? config.filter : null);
            if (references != null) {
                serviceReferences.addAll(Arrays.asList(references));
            } else {
                ServiceReference<?>[] all = bundleContext.getAllServiceReferences(null,
                        StringUtils.isNotBlank(config.filter) ? config.filter : null);
                for (ServiceReference<?> ref : all) {
                    Object service;
                    if (config.serviceType.equals(ref.getProperty("service.pid"))
                            || (!config.serviceType.contains("~")
                            && config.serviceType.equals(ref.getProperty("service.factoryPid")))
                            || ((service = bundleContext.getService(ref)) != null
                            && config.serviceType.equals(service.getClass().getName()))) {
                        serviceReferences.add(ref);
                        break;
                    }
                }
            }
        } catch (InvalidSyntaxException ignore) {
        }
        return serviceReferences;
    }
}
