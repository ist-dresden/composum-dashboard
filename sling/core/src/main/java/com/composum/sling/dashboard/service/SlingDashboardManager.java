package com.composum.sling.dashboard.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.settings.SlingSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.DashboardConfig.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.NT_UNSTRUCTURED;
import static com.composum.sling.dashboard.DashboardConfig.patternList;


@Component(
        service = {DashboardManager.class, ResourceFilter.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = SlingDashboardManager.Config.class)
public class SlingDashboardManager implements DashboardManager, ResourceFilter {

    public static final String JOB_TOPIC = "com/composum/dashboard/content/create";

    @ObjectClassDefinition(name = "Composum Sling Dashboard Manager")
    public @interface Config {

        @AttributeDefinition(name = "Allowed Property Patterns")
        String[] allowedPropertyPatterns() default {
                "^.*$"
        };

        @AttributeDefinition(name = "Disabled Property Patterns")
        String[] disabledPropertyPatterns() default {
                "^rep:.*$",
                "^.*password.*$"
        };

        @AttributeDefinition(name = "Allowed Path Patterns")
        String[] allowedPathPatterns() default {
                "^/$",
                "^/content(/.*)?$",
                "^/conf(/.*)?$",
                "^/var(/.*)?$",
                "^/mnt(/.*)?$"
        };

        @AttributeDefinition(name = "Disabled Path Patterns")
        String[] disabledPathPatterns() default {
                ".*/rep:.*",
                "^(/.*)?/api(/.*)?$"
        };

        @AttributeDefinition(name = "Sortable Types")
        String[] sortableTypes() default {
                "nt:folder", "sling:Folder"
        };

        @AttributeDefinition(name = "CSS Runmodes")
        String[] cssRunmodes();

        @AttributeDefinition(name = "Content Page Type")
        String contentPageType() default "[nt:unstructured]";

        @AttributeDefinition(name = "Content Page Type")
        String[] pageGeneration();

        @AttributeDefinition(name = "Login URI")
        String loginUri() default "/system/sling/form/login.html";
    }

    protected static final String SA_WIDGETS = SlingDashboardManager.class.getName() + "#";

    protected static final Pattern CONTENT_TYPE = Pattern.compile("^(?<name>[^\\[]+)?\\[(?<type>.+)]$");

    public static final String[] DATE_FORMATS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss.SSS Z",
            "yyyy-MM-dd HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss"
    };

    @Reference
    private SlingSettingsService settingsService;

    protected List<Pattern> allowedPropertyPatterns;
    protected List<Pattern> disabledPropertyPatterns;
    protected List<Pattern> allowedPathPatterns;
    protected List<Pattern> disabledPathPatterns;
    protected List<String> sortableTypes;
    protected List<String> cssRunmodes;
    protected List<String> contentPageType;
    protected List<String> pageGeneration;
    protected String loginUri;

    protected final List<DashboardPlugin> dashboardPlugins = new ArrayList<>();

    @Reference(
            service = DashboardPlugin.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY
    )
    protected void bindDashboardPlugin(@NotNull final DashboardPlugin plugin) {
        synchronized (dashboardPlugins) {
            dashboardPlugins.add(plugin);
            dashboardPlugins.sort(Comparator.comparingInt(DashboardPlugin::getRank));
        }
    }

    protected void unbindDashboardPlugin(@NotNull final DashboardPlugin plugin) {
        synchronized (dashboardPlugins) {
            dashboardPlugins.remove(plugin);
        }
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        allowedPropertyPatterns = patternList(config.allowedPropertyPatterns());
        disabledPropertyPatterns = patternList(config.disabledPropertyPatterns());
        allowedPathPatterns = patternList(config.allowedPathPatterns());
        disabledPathPatterns = patternList(config.disabledPathPatterns());
        sortableTypes = Arrays.asList(Optional.ofNullable(config.sortableTypes()).orElse(new String[0]));
        cssRunmodes = Arrays.asList(Optional.ofNullable(config.cssRunmodes()).orElse(new String[0]));
        contentPageType = Arrays.asList(StringUtils.split(config.contentPageType(), "/"));
        pageGeneration = Arrays.asList(config.pageGeneration());
        loginUri = config.loginUri();
    }

    @Override
    public boolean isAllowedProperty(@NotNull final String name) {
        for (Pattern allowed : allowedPropertyPatterns) {
            if (allowed.matcher(name).matches()) {
                for (Pattern disabled : disabledPropertyPatterns) {
                    if (disabled.matcher(name).matches()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAllowedResource(@NotNull final Resource resource) {
        final String path = resource.getPath();
        for (Pattern allowed : allowedPathPatterns) {
            if (allowed.matcher(path).matches()) {
                for (Pattern disabled : disabledPathPatterns) {
                    if (disabled.matcher(path).matches()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable Resource getRequestResource(@NotNull final SlingHttpServletRequest request) {
        Resource resource = Optional.ofNullable(request.getRequestPathInfo().getSuffixResource())
                .orElse(request.getResourceResolver().getResource("/"));
        return resource != null && isAllowedResource(resource) ? resource : null;
    }

    @Override
    public boolean isSortableType(@Nullable final String type) {
        return StringUtils.isNotBlank(type) && sortableTypes.contains(type);
    }

    @Override
    public void addRunmodeCssClasses(Set<String> cssClassSet) {
        final Set<String> slingRunmodes = settingsService.getRunModes();
        for (final String runmode : cssRunmodes) {
            if (slingRunmodes.contains(runmode)) {
                cssClassSet.add("runmode-" + runmode);
            }
        }
    }

    @Override
    public @NotNull String getLoginUri() {
        return loginUri;
    }

    @Override
    public @Nullable DashboardWidget getWidget(@NotNull final SlingHttpServletRequest request,
                                               @Nullable final String context, @NotNull final String name) {
        for (DashboardWidget widget : getWidgets(request, null)) {
            if (name.equals(widget.getName())) {
                return widget;
            }
        }
        return null;
    }

    @Override
    public Collection<DashboardWidget> getWidgets(@NotNull final SlingHttpServletRequest request,
                                                  @Nullable final String context) {
        Map<String, DashboardWidget> wigetSet = new TreeMap<>();
        for (DashboardPlugin plugin : dashboardPlugins) {
            plugin.provideWidgets(request, context, wigetSet);
        }
        List<DashboardWidget> widgets = new ArrayList<>(wigetSet.values());
        widgets.sort(DashboardWidget.COMPARATOR);
        return widgets;
    }

    @Override
    public boolean createContentPage(@NotNull final SlingHttpServletRequest request,
                                     @NotNull final SlingHttpServletResponse response,
                                     @NotNull final String path, @Nullable final ContentGenerator generator,
                                     String... jsonContent) {
        final ResourceResolver resolver = request.getResourceResolver();
        if (path.matches("^(/[^/]+){2,}.*$")) {
            try {
                Resource parent = provideParent(resolver, StringUtils.substringBeforeLast(path, "/"), "sling:Folder");
                String name = StringUtils.substringAfterLast(path, "/");
                String primaryType = NT_UNSTRUCTURED;
                Resource current = null;
                if (generator != null) {
                    for (int i = 0; i < contentPageType.size(); i++) {
                        final Matcher matcher = CONTENT_TYPE.matcher(contentPageType.get(i));
                        if (matcher.matches()) {
                            name = StringUtils.defaultString(matcher.group("name"), name);
                            primaryType = matcher.group("type");
                            if (i < contentPageType.size() - 1) {
                                if ((current = parent.getChild(name)) != null) {
                                    resolver.delete(current);
                                }
                                parent = resolver.create(parent, name, Collections.singletonMap(JCR_PRIMARY_TYPE, primaryType));
                            }
                        }
                    }
                    if ((current = parent.getChild(name)) != null) {
                        resolver.delete(current);
                    }
                    current = generator.createContent(request, parent, name, primaryType);
                }
                if (jsonContent != null && jsonContent.length > 0) {
                    if (current == null) {
                        current = parent.getChild(name);
                        if (current == null) {
                            current = resolver.create(parent, name, Collections.singletonMap(JCR_PRIMARY_TYPE, primaryType));
                        }
                    }
                    for (final String json : jsonContent) {
                        loadJsonContent(current, null, json);
                    }
                }
                return true;
            } catch (IOException ignore) {
            }
        }
        return false;
    }

    protected @NotNull Resource provideParent(@NotNull final ResourceResolver resolver,
                                              @NotNull final String parentPath, @NotNull final String primaryType)
            throws PersistenceException {
        Resource resource = resolver.getResource(parentPath);
        if (resource == null) {
            Resource parent = provideParent(resolver, StringUtils.substringBeforeLast(parentPath, "/"), primaryType);
            resource = resolver.create(parent, StringUtils.substringAfterLast(parentPath, "/"),
                    Collections.singletonMap(JCR_PRIMARY_TYPE, primaryType));
        }
        return resource;
    }

    protected void loadJsonContent(@NotNull final Resource resource, @Nullable String name, @NotNull final String jsonContent)
            throws IOException {
        JsonElement element = new JsonParser().parse(jsonContent);
        if (element.isJsonObject()) {
            loadJsonContent(resource, name, element.getAsJsonObject());
        }
    }

    protected void loadJsonContent(@NotNull final Resource parent,
                                   @Nullable final String name, @NotNull final JsonObject object)
            throws IOException {
        final ResourceResolver resolver = parent.getResourceResolver();
        Map<String, Object> properties = new HashMap<>();
        for (String memberName : object.keySet()) {
            JsonElement member = object.get(memberName);
            if (!member.isJsonObject()) {
                if (member.isJsonArray()) {
                    List<Object> values = new ArrayList<>();
                    for (JsonElement item : member.getAsJsonArray()) {
                        Object value = getJsonValue(item);
                        if (value != null) {
                            values.add(object);
                        }
                    }
                    properties.put(memberName, values.toArray());
                } else {
                    Object value = getJsonValue(member);
                    if (value != null) {
                        properties.put(memberName, value);
                    }
                }
            }
        }
        Resource resource = StringUtils.isNotBlank(name) ? parent.getChild(name) : parent;
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank((String) properties.get(JCR_PRIMARY_TYPE))) {
            if (resource != null) {
                resolver.delete(resource);
            }
            resource = resolver.create(parent, name, properties);
        } else {
            if (resource != null) {
                if (!properties.isEmpty()) {
                    final ModifiableValueMap values = resource.adaptTo(ModifiableValueMap.class);
                    if (values != null) {
                        values.putAll(properties);
                    }
                }
            } else {
                if (StringUtils.isNotBlank(name)) {
                    properties.put(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED);
                    resource = resolver.create(parent, name, properties);
                }
            }
        }
        if (resource != null) {
            for (String memberName : object.keySet()) {
                JsonElement member = object.get(memberName);
                if (member.isJsonObject()) {
                    loadJsonContent(resource, memberName, member.getAsJsonObject());
                }
            }
        }
    }

    protected Object getJsonValue(JsonElement element) {
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                return primitive.getAsLong();
            } else {
                String string = primitive.getAsString();
                for (String dateFormat : DATE_FORMATS) {
                    try {
                        Date date = new SimpleDateFormat(dateFormat).parse(string);
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        return calendar;
                    } catch (ParseException ignore) {
                    }
                }
                return string;
            }
        }
        return null;
    }
}
