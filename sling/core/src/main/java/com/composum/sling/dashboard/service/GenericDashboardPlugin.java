package com.composum.sling.dashboard.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.jcr.query.Query;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(service = DashboardPlugin.class)
@Designate(ocd = GenericDashboardPlugin.Config.class, factory = true)
public class GenericDashboardPlugin implements DashboardPlugin {

    @ObjectClassDefinition(name = "Composum Dashboard Generic Widgets",
            description = "provides a set of widgets declared by content resources and their resource type"
    )
    public @interface Config {

        @AttributeDefinition(name = "Resource Type", description =
                "the component resource type of the generic widgets provided by this configuration")
        String resourceType();

        @AttributeDefinition(name = "Search Root", description =
                "the repository root folder for searching widgets declared by content resources")
        String searchRoot() default "/content";

        @AttributeDefinition(name = "Widget Context", description =
                "if specified all matching widget implementations are registred as widget services also")
        String[] widgetContext();

        @AttributeDefinition(name = "Rank")
        int rank() default 9000;

        @AttributeDefinition()
        String webconsole_configurationFactory_nameHint() default "'{resourceType}' @ '{searchRoot}'";
    }

    protected static class Widget implements DashboardWidget, Serializable {

        protected final String name;
        protected final String path;
        protected final String resourceType;
        protected final ValueMap properties;

        public Widget(@NotNull final Resource resource) {
            name = resource.getName();
            path = resource.getPath();
            resourceType = resource.getResourceType();
            properties = new ValueMapDecorator(new HashMap<>(resource.getValueMap()));
        }

        @Override
        public @NotNull Resource getWidgetResource(@NotNull final SlingHttpServletRequest request) {
            final String resourceType = properties.get("widgetResourceType", String.class);
            final ResourceResolver resolver = request.getResourceResolver();
            Resource resource = resolver.getResource(path);
            if (resource != null) {
                if (StringUtils.isNotBlank(resourceType)) {
                    resource = new ResourceWrapper(resource) {
                        @Override
                        public @NotNull String getResourceType() {
                            return resourceType;
                        }
                    };
                }
                return resource;
            }
            return new SyntheticResource(resolver, path, resourceType);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DashboardWidget && getName().equals(((DashboardWidget) other).getName());
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

        @Override
        public @NotNull String getName() {
            return name;
        }

        @Override
        public @NotNull String getLabel() {
            return properties.get("title", properties.get("jcr:title", getName()));
        }

        @Override
        public @NotNull String getNavTitle() {
            return properties.get("navTitle", getLabel());
        }

        @Override
        public @NotNull <T> T getProperty(@NotNull String name, @NotNull T defaultValue) {
            return properties.get(name, defaultValue);
        }

        @Override
        public @NotNull Collection<String> getContext() {
            return Arrays.asList(properties.get("context", new String[0]));
        }

        @Override
        public @NotNull Collection<String> getCategory() {
            return Arrays.asList(properties.get("category", new String[]{getName()}));
        }

        @Override
        public int getRank() {
            return properties.get("rank", 0L).intValue();
        }

        @Override
        public void embedScript(@NotNull final PrintWriter writer, @NotNull final String mode) {
        }
    }

    protected static final String SA_WIDGETS = GenericDashboardPlugin.class.getName() + "#";

    public static final String WIDGET_QUERY_FMT = "/jcr:root%s//*[@sling:resourceType='%s']";

    protected String resourceType;
    protected String searchRoot;
    protected List<String> widgetContext;
    protected int rank;

    protected final Map<String, DashboardWidget> widgetServices = new HashMap<>();

    @Reference(
            service = DashboardWidget.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY
    )
    protected void addDashboardWidget(@NotNull final DashboardWidget widget) {
        if (isMatchingWidget(widget)) {
            synchronized (widgetServices) {
                widgetServices.put(widget.getName(), widget);
            }
        }
    }

    protected void removeDashboardWidget(@NotNull final DashboardWidget widget) {
        if (isMatchingWidget(widget)) {
            synchronized (widgetServices) {
                widgetServices.remove(widget.getName());
            }
        }
    }

    protected boolean isMatchingWidget(@NotNull final DashboardWidget widget) {
        for (String context : widgetContext) {
            if (widget.getContext().contains(context)) {
                return true;
            }
        }
        return false;
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        resourceType = config.resourceType();
        searchRoot = config.searchRoot();
        widgetContext = Arrays.asList(Optional.ofNullable(config.widgetContext()).orElse(new String[0]));
        rank = config.rank();
    }

    @Override
    public int getRank() {
        return rank;
    }

    @SuppressWarnings("deprecated")
    @Override
    public void provideWidgets(@NotNull final SlingHttpServletRequest request, @Nullable final String context,
                               @NotNull final Map<String, DashboardWidget> widgetSet) {
        final String query = String.format(WIDGET_QUERY_FMT, searchRoot, resourceType);
        final Iterator<Resource> widgetResources = request.getResourceResolver().findResources(query, Query.XPATH);
        while (widgetResources.hasNext()) {
            Widget widget = new Widget(widgetResources.next());
            if ((context == null || widget.getContext().contains(context))
                    && !widgetSet.containsKey(widget.getName())) {
                widgetSet.put(widget.getName(), widget);
            }
        }
        for (DashboardWidget widget : widgetServices.values()) {
            if ((context == null || widget.getContext().contains(context))
                    && !widgetSet.containsKey(widget.getName())) {
                widgetSet.put(widget.getName(), widget);
            }
        }
    }
}
