package com.composum.sling.dashboard.servlet;

import com.composum.sling.dashboard.service.ResourceFilter;
import com.composum.sling.dashboard.service.XmlRenderer;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_CONTENT;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_CREATED;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_DATA;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_LAST_MODIFIED;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_MIME_TYPE;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_MIXIN_TYPES;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.NT_FILE;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.NT_RESOURCE;
import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.NT_UNSTRUCTURED;

@Component(service = {Servlet.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardExtractServlet.Config.class)
public class DashboardExtractServlet extends SlingAllMethodsServlet {

    public enum Mode {MERGE, UPDATE, REPLACE}

    @ObjectClassDefinition(
            name = "Composum Dashboard Extract Service configuration"
    )
    @interface Config {

        @AttributeDefinition(
                name = "Predefined Paths",
                description = "the set of resource paths to extract in the 'predefined' mode"
        )
        String[] predefinedPaths();

        @AttributeDefinition(
                name = "Path Rule Set",
                description = "source to target mapping rules; e.g. '/content/dam(/[^/]+(/.*)),dam:Asset,1,/content/dam/test$2'"
        )
        String[] pathRuleSet();

        @AttributeDefinition(
                name = "add. ZIP Entries",
                description = "entry patterns to add to ZIP; e.g. 'dam:Asset,jcr:content/renditions/original'"
        )
        String[] addZipEntries();

        @AttributeDefinition(
                name = "Ignored Children",
                description = "path patterns for resources children to ignore; e.g. '^.+/jcr:content/renditions/cq5dam\\..+$'"
        )
        String[] ignoredChildren();

        @AttributeDefinition(
                name = "Ignored Properties",
                description = "name patterns of properties to ignore; e.g. '^jcr:(created|lastModified).*$'"
        )
        String[] ignoredProperties() default {
                "^jcr:(uuid)$",
                "^jcr:(baseVersion|predecessors|versionHistory|isCheckedOut)$",
                "^jcr:(created|lastModified).*$",
                "^cq:last(Modified|Replicat|Rolledout).*$"
        };

        @AttributeDefinition(name = "Servlet Methods",
                description = "the HTTP methods supported by this servlet")
        String[] sling_servlet_methods() default {
                HttpConstants.METHOD_GET
        };

        @AttributeDefinition(name = "Servlet Selectors",
                description = "the Sling selectors supported by this servlet")
        String[] sling_servlet_selectors() default {
                "extract.paths",
                "extract.scan",
                "extract.copy",
                "extract.copy.zip"
        };

        @AttributeDefinition(name = "Resource Types",
                description = "the Sling resource types implemented by this servlet")
        String[] sling_servlet_resourceTypes() default {
                ServletResolverConstants.DEFAULT_RESOURCE_TYPE
        };

        @AttributeDefinition(name = "Servlet Extensions",
                description = "the possible URL extensions supported by this servlet")
        String[] sling_servlet_extensions() default {
                "txt",
                "json"
        };

        @AttributeDefinition(name = "Servlet Paths",
                description = "the servlet paths if this configuration variant should be supported")
        String[] sling_servlet_paths();
    }

    private static final Logger LOG = LoggerFactory.getLogger(DashboardExtractServlet.class);

    public class ExtractSession {

        private final ResourceResolver resolver;
        private final Mode mode;
        private final int levelMax;
        private final Set<String> sourcePathSet;
        private final Set<String> targetPathSet;
        private final Map<String, String> sourceToTarget;
        private final Set<String> outsidePaths;
        private final Set<String> missedPaths;
        private final Map<String, Set<String>> pathSets;

        private ExtractSession(@NotNull final ResourceResolver resolver, @Nullable final Mode mode, final int levelMax) {
            this.resolver = resolver;
            this.mode = mode != null ? mode : Mode.MERGE;
            this.levelMax = levelMax;
            sourcePathSet = new TreeSet<>();
            targetPathSet = new TreeSet<>();
            sourceToTarget = new HashMap<>();
            outsidePaths = new TreeSet<>();
            missedPaths = new TreeSet<>();
            pathSets = new LinkedHashMap<>();
            pathSets.put("source", sourcePathSet);
            pathSets.put("target", targetPathSet);
            pathSets.put("outside", outsidePaths);
            pathSets.put("missed", missedPaths);
        }

        public @NotNull ResourceResolver getResolver() {
            return resolver;
        }

        public @Nullable Resource getResource(@Nullable final String path) {
            return StringUtils.isNotBlank(path) ? resolver.getResource(path) : null;
        }

        public Set<String> getSourcePathSet() {
            return sourcePathSet;
        }

        public Set<String> getTargetPathSet() {
            return targetPathSet;
        }

        public @Nullable String getTargetPath(@Nullable final String sourcePath) {
            return StringUtils.isNotBlank(sourcePath) ? sourceToTarget.get(sourcePath) : null;
        }

        public Map<String, Set<String>> getPathSets() {
            return pathSets;
        }

        // scan

        public void scanExtractPaths(@NotNull final ResourceResolver resolver, @NotNull final String... paths) {
            for (final String path : paths) {
                if ("pre".equals(path) || "predefined".equals(path)) {
                    if (predefinedPaths != null) {
                        scanExtractPaths(resolver, predefinedPaths);
                    }
                } else {
                    final Resource resource = resolver.getResource(path);
                    if (resource != null) {
                        registerReferenceCandidate(resource.getPath(), 0);
                    } else {
                        missedPaths.add(path);
                    }
                }
            }
        }

        public void scanExtractPaths(@NotNull final Resource... target) {
            for (final Resource resource : target) {
                registerReferenceCandidate(resource.getPath(), 0);
            }
        }

        protected void registerReferenceCandidate(@NotNull final String path, int level) {
            if (level < levelMax && !sourcePathSet.contains(path)) {
                final Resource resource = resolver.getResource(path);
                if (resource != null) {
                    final PathRule pathRule = getPathRule(path, resource);
                    if (pathRule != null) {
                        sourcePathSet.add(path);
                        collectNeededResources(level + 1, resource, 0, pathRule.maxDepth);
                    } else {
                        outsidePaths.add(path);
                    }
                    registerParentReferences(resource);
                } else {
                    missedPaths.add(path);
                }
            }
        }

        protected void registerParentReferences(@NotNull final Resource resource) {
            final Resource parent = resource.getParent();
            if (parent != null && !sourcePathSet.contains(parent.getPath())) {
                final PathRule parentRule = getPathRule(parent.getPath(), parent);
                if (parentRule != null) {
                    sourcePathSet.add(parent.getPath());
                    collectNeededResources(0, parent, 0, parentRule.maxDepth);
                    registerParentReferences(parent);
                }
            }
        }

        protected void collectNeededResources(int level, @NotNull final Resource target, int depth, @Nullable Integer maxDepth) {
            if (JCR_CONTENT.equals(target.getName())) {
                maxDepth = null;
            }
            if (maxDepth == null || depth < maxDepth) {
                findPathReferences(level, target.getValueMap());
                for (final Resource child : target.getChildren()) {
                    collectNeededResources(level, child, depth + 1, maxDepth);
                }
            }
        }

        protected void findPathReferences(int level, @NotNull final ValueMap values) {
            for (final Map.Entry<String, Object> entry : values.entrySet()) {
                final String key = entry.getKey();
                if (isAllowedProperty(key)) {
                    final Object value = entry.getValue();
                    if (value instanceof String) {
                        findPathReferences(level, (String) value);
                    } else if (value instanceof String[]) {
                        for (final String item : (String[]) value) {
                            findPathReferences(level, item);
                        }
                    }
                }
            }
        }

        protected void findPathReferences(int level, @Nullable final String value) {
            if (StringUtils.isNotBlank(value)) {
                if (value.startsWith("/")) {
                    registerReferenceCandidate(value, level);
                } else {
                    final Matcher matcher = Pattern.compile("\"(?<path>/[^\"]+)\"").matcher(value);
                    final int length = value.length();
                    int pos = 0;
                    while (pos < length && matcher.find(pos)) {
                        registerReferenceCandidate(matcher.group("path"), level);
                        pos = matcher.end() + 1;
                    }
                }
            }
        }

        // copy

        public @NotNull Collection<String> copyExtractPaths(boolean dryRun)
                throws PersistenceException {
            final ExtractFilter filter = new ExtractFilter();
            for (final String sourcePath : getSourcePathSet()) {
                final Resource source = getResource(sourcePath);
                if (source != null) {
                    final PathRule pathRule = getPathRule(sourcePath, source);
                    if (pathRule != null && StringUtils.isNotBlank(pathRule.targetPathPattern)) {
                        final Matcher sourceMatcher = pathRule.sourcePathPattern.matcher(sourcePath);
                        if (sourceMatcher.matches()) {
                            final String targetPath = sourceMatcher.replaceFirst(pathRule.targetPathPattern);
                            if (StringUtils.isNotBlank(targetPath) && !targetPath.equals(sourcePath)) {
                                extractCopy(source, targetPath, dryRun, filter);
                            }
                        }
                    }
                }
            }
            adjustPathReferences();
            return getTargetPathSet();
        }

        protected Resource extractCopy(@NotNull final Resource source, @NotNull final String targetPath, boolean dryRun,
                                       @NotNull final ResourceFilter filter)
                throws PersistenceException {
            Resource target = resolver.getResource(targetPath);
            if (target == null && dryRun) {
                target = resolver.resolve(targetPath);
            } else {
                final Resource parent = provideParent(source, targetPath, filter);
                target = copyResource(source, parent, source.getName(), 0, filter);
            }
            sourceToTarget.put(source.getPath(), targetPath);
            targetPathSet.add(targetPath);
            return target;
        }

        protected Resource copyResource(@NotNull final Resource source,
                                        @NotNull final Resource parent, @NotNull final String name, final int depth,
                                        @NotNull final ResourceFilter filter)
                throws PersistenceException {
            Resource target = parent.getChild(name);
            if (target == null || mode == Mode.REPLACE) {
                if (target != null) {
                    resolver.delete(target);
                }
                LOG.info("copyResource({},{},{}).create", source.getPath(), parent.getPath(), name);
                target = resolver.create(parent, name, copyProperties(source, filter, null));
            } else {
                final ModifiableValueMap targetProps = target.adaptTo(ModifiableValueMap.class);
                if (targetProps != null) {
                    targetProps.putAll(copyProperties(source, filter, mode == Mode.UPDATE ? null : targetProps));
                }
            }
            for (final Resource child : source.getChildren()) {
                if (filter.isAllowedResource(child)) {
                    final PathRule pathRule = getPathRule(source.getPath(), source);
                    if (child.getPath().matches("^.*/" + JCR_CONTENT + "(/.*)?$")
                            || (pathRule != null && (pathRule.maxDepth == null || depth + 1 < pathRule.maxDepth))) {
                        copyResource(child, target, child.getName(), depth + 1, filter);
                    }
                }
            }
            return target;
        }

        protected @NotNull Resource provideParent(@NotNull final Resource source, @NotNull final String targetPath,
                                                  @NotNull final ResourceFilter filter)
                throws PersistenceException {
            final String parentPath = StringUtils.substringBeforeLast(targetPath, "/");
            Resource parent = resolver.getResource(parentPath);
            if (parent == null && !targetPath.equals(parentPath)) {
                final Resource sourceParent = source.getParent();
                if (sourceParent != null) {
                    final Resource parentParent = provideParent(sourceParent, parentPath, filter);
                    final String parentName = StringUtils.substringAfterLast(parentPath, "/");
                    LOG.info("provideParent({},{}).create({},{})", source.getPath(), targetPath, parentParent.getPath(), parentName);
                    parent = resolver.create(parentParent, parentName, copyProperties(sourceParent, filter, null));
                    sourceToTarget.put(sourceParent.getPath(), parentPath);
                    targetPathSet.add(parentPath);
                    final Resource sourceParentContent = sourceParent.getChild(JCR_CONTENT);
                    if (sourceParentContent != null) {
                        copyResource(sourceParentContent, parent, JCR_CONTENT, 0, filter);
                    }
                }
            }
            if (parent == null) {
                throw new PersistenceException("can't create parent (" + targetPath + ")");
            }
            return parent;
        }

        public void adjustPathReferences() {
            for (final String targetPath : getTargetPathSet()) {
                final Resource target = getResource(targetPath);
                if (target != null) {
                    adjustPathReferences(target);
                }
            }
        }

        protected void adjustPathReferences(@NotNull final Resource target) {
            final ModifiableValueMap properties = target.adaptTo(ModifiableValueMap.class);
            if (properties != null) {
                for (final Map.Entry<String, Object> entry : target.getValueMap().entrySet()) {
                    Object value = entry.getValue();
                    boolean modified = false;
                    if (value instanceof String) {
                        final String changed = adjustPathReferences((String) value);
                        if (changed != null) {
                            value = changed;
                            modified = true;
                        }
                    } else if (value instanceof String[]) {
                        final String[] multi = (String[]) value;
                        for (int i = 0; i < multi.length; i++) {
                            final String chngd = adjustPathReferences(multi[i]);
                            if (chngd != null) {
                                multi[i] = chngd;
                                modified = true;
                            }
                        }
                    }
                    if (modified) {
                        properties.put(entry.getKey(), value);
                    }
                }
            }
            for (final Resource child : target.getChildren()) {
                adjustPathReferences(child);
            }
        }

        protected @Nullable String adjustPathReferences(@Nullable final String value) {
            if (StringUtils.isNotBlank(value)) {
                if (value.startsWith("/")) {
                    return getTargetPath(value);
                } else {
                    boolean modified = false;
                    final StringBuilder buffer = new StringBuilder();
                    final Matcher matcher = Pattern.compile("\"(?<path>/[^\"]+)\"").matcher(value);
                    final int length = value.length();
                    int pos = 0;
                    while (pos < length && matcher.find(pos)) {
                        buffer.append(value, pos, matcher.start());
                        final String targetPath = getTargetPath(matcher.group("path"));
                        if (targetPath != null) {
                            buffer.append('"').append(targetPath).append('"');
                            modified = true;
                        } else {
                            buffer.append(value, matcher.start(), matcher.end());
                        }
                        pos = matcher.end() + 1;
                    }
                    if (modified) {
                        if (pos < length) {
                            buffer.append(value.substring(pos));
                        }
                        return buffer.toString();
                    }
                }
            }
            return null;
        }

        // zip

        protected Set<String> zipEntrySet() {
            ExtractFilter filter = new ExtractFilter();
            final Set<String> entrySet = new TreeSet<>();
            for (final String path : getTargetPathSet()) {
                for (String p = path; StringUtils.isNotBlank(p); p = StringUtils.substringBeforeLast(p, "/")) {
                    if (resolver.getResource(path) != null) {
                        entrySet.add(p);
                    }
                }
                final Resource resource = resolver.getResource(path);
                if (resource != null && filter.isAllowedResource(resource)) {
                    final ValueMap properties = resource.getValueMap();
                    for (final Map.Entry<Pattern, List<String>> entry : addZipEntries.entrySet()) {
                        String addPath = null;
                        final Pattern pattern = entry.getKey();
                        Matcher matcher;
                        if (pattern.matcher(properties.get(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED)).matches()) {
                            for (final String add : entry.getValue()) {
                                final Resource res = resolver.getResource(resource.getPath()
                                        + (add.startsWith("/") ? add : "/" + add));
                                if (res != null && filter.isAllowedResource(res)) {
                                    entrySet.add(res.getPath());
                                }
                            }
                        } else if ((matcher = pattern.matcher(resource.getPath())).matches()) {
                            for (final String add : entry.getValue()) {
                                final Resource res = resolver.getResource(resource.getPath()
                                        + matcher.replaceFirst(add));
                                if (res != null && filter.isAllowedResource(res)) {
                                    entrySet.add(res.getPath());
                                }
                            }
                        }
                    }
                }
            }
            return entrySet;
        }

        public void writeArchive(@NotNull OutputStream output)
                throws IOException {
            try (final ZipOutputStream zipStream = new ZipOutputStream(output)) {
                final Set<String> entrySet = zipEntrySet();
                for (String path : entrySet) {
                    writeIntoZip(zipStream, entrySet, resolver.getResource(path));
                }
                zipStream.flush();
            }
        }

        protected void writeIntoZip(@NotNull ZipOutputStream zipStream, @NotNull final Set<String> entrySet,
                                    @Nullable final Resource resource) throws IOException {
            if (resource == null || ResourceUtil.isNonExistingResource(resource)) {
                return;
            }
            final ValueMap properties = resource.getValueMap();
            final String primaryType = properties.get(JCR_PRIMARY_TYPE, "");
            if (NT_RESOURCE.equals(primaryType)) {
                return;
            }
            final String name = resource.getName();
            final String zipName = resource.getPath().replaceAll("/jcr:content(/.+)?", "/_jcr_content$1");
            ZipEntry entry;
            if (NT_FILE.equals(primaryType)) {
                final Resource content = resource.getChild(JCR_CONTENT);
                final ValueMap contentProps = Optional.ofNullable(content).map(Resource::getValueMap)
                        .orElse(new ValueMapDecorator(Collections.emptyMap()));
                try (InputStream data = Optional.ofNullable(contentProps.get(JCR_DATA, InputStream.class))
                        .orElse(properties.get(JCR_DATA, InputStream.class))) {
                    if (data != null) {
                        if (properties.get(JCR_MIME_TYPE, String.class) != null
                                || contentProps.get(JCR_MIME_TYPE, String.class) != null
                                || properties.get(JCR_MIXIN_TYPES, new String[0]).length > 0
                                || contentProps.get(JCR_MIXIN_TYPES, new String[0]).length > 0
                                || !NT_RESOURCE.equals(contentProps.get(JCR_PRIMARY_TYPE, String.class))) {
                            // 'decorated' file, not only a primitive file...
                            entry = new ZipEntry(zipName + ".dir/.content.xml");
                            setLastModified(entry, resource);
                            zipStream.putNextEntry(entry);
                            writeXmlToZip(zipStream, resource, 1);
                            zipStream.closeEntry();
                        }
                        entry = new ZipEntry(zipName);
                        setLastModified(entry, resource);
                        zipStream.putNextEntry(entry);
                        IOUtils.copy(data, zipStream);
                        zipStream.closeEntry();
                    }
                }
            } else if (StringUtils.isNotBlank(primaryType) && xmlRenderer != null) {
                final boolean isFolder = primaryType.matches("^(sling|nt):(Ordered)?[Ff]older$");
                final boolean hasContent = resource.getChild(JCR_CONTENT) != null;
                entry = new ZipEntry(zipName + "/.content.xml");
                setLastModified(entry, resource);
                zipStream.putNextEntry(entry);
                writeXmlToZip(zipStream, resource, isFolder || hasContent ? 1 : null);
                zipStream.closeEntry();
            }
        }

        protected void writeXmlToZip(@NotNull ZipOutputStream zipStream,
                                     @NotNull final Resource resource, @Nullable final Integer maxDepth) {
            try {
                final ExtractFilter filter = new ExtractFilter();
                final PrintWriter writer = new PrintWriter(zipStream);
                xmlRenderer.dumpXml(writer, "", resource, 0, maxDepth,
                        filter, filter::isAllowedProperty, xmlRenderer::isAllowedMixin);
                writer.flush();
            } catch (RepositoryException ignore) {
            }
        }

        protected void setLastModified(ZipEntry entry, Resource resource) {
            final Long lastModified = getLastModified(resource);
            if (lastModified != null) {
                entry.setLastModifiedTime(FileTime.fromMillis(lastModified));
            }
        }

        protected Long getLastModified(@Nullable final Resource resource) {
            Calendar time = null;
            if (resource != null) {
                ValueMap properties = resource.getValueMap();
                time = properties.get(JCR_LAST_MODIFIED, Calendar.class);
                if (time == null) {
                    time = properties.get(JCR_CREATED, Calendar.class);
                }
                if (time == null) {
                    return getLastModified(resource.getChild(JCR_CONTENT));
                }
            }
            return time != null ? time.getTimeInMillis() : null;
        }
    }

    public static class PathRule {

        public final Pattern sourcePathPattern;
        public final Pattern sourceTypePattern;
        public final Integer maxDepth;
        public final String targetPathPattern;

        public PathRule(@NotNull final String expression) {
            final String[] parts = StringUtils.split(expression, ",", 4);
            sourcePathPattern = Pattern.compile(parts[0]);
            sourceTypePattern = parts.length > 1 && StringUtils.isNotBlank(parts[1]) &&
                    !"*".equals(parts[1]) ? Pattern.compile(parts[1]) : null;
            maxDepth = parts.length > 2 && StringUtils.isNotBlank(parts[2]) &&
                    !"*".equals(parts[2]) ? Integer.parseInt(parts[2]) : null;
            targetPathPattern = parts.length > 3 && StringUtils.isNotBlank(parts[3]) ? parts[3] : null;
        }
    }

    public class ExtractFilter implements ResourceFilter {

        @Override
        public boolean isAllowedProperty(@NotNull String name) {
            return (resourceFilter == null || resourceFilter.isAllowedProperty(name))
                    && DashboardExtractServlet.this.isAllowedProperty(name);
        }

        @Override
        public boolean isAllowedResource(@NotNull Resource resource) {
            return (resourceFilter == null || resourceFilter.isAllowedResource(resource))
                    && DashboardExtractServlet.this.isAllowedChild(resource.getPath());
        }

        @Override
        public @Nullable Resource getRequestResource(@NotNull SlingHttpServletRequest request) {
            return resourceFilter != null ? resourceFilter.getRequestResource(request) : request.getResource();
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private ResourceFilter resourceFilter;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private XmlRenderer xmlRenderer;

    protected String[] predefinedPaths;
    protected List<PathRule> pathRuleSet;
    protected List<Pattern> ignoredChildren;
    protected List<Pattern> ignoredProperties;
    protected Map<Pattern, List<String>> addZipEntries;

    @Activate
    @Modified
    protected void activate(Config config) {
        predefinedPaths = config.predefinedPaths();
        pathRuleSet = new ArrayList<>();
        for (final String expression : config.pathRuleSet()) {
            if (StringUtils.isNotBlank(expression)) {
                pathRuleSet.add(new PathRule(expression));
            }
        }
        ignoredChildren = patternList(config.ignoredChildren());
        ignoredProperties = patternList(config.ignoredProperties());
        addZipEntries = new LinkedHashMap<>();
        for (final String expression : config.addZipEntries()) {
            if (StringUtils.isNotBlank(expression)) {
                String[] parts = StringUtils.split(expression, ",");
                addZipEntries.put(Pattern.compile(parts[0]),
                        Arrays.stream(parts).skip(1).collect(Collectors.toList()));
            }
        }
    }

    protected @Nullable PathRule getPathRule(@Nullable final String path, @Nullable final Resource resource) {
        if (StringUtils.isNotBlank(path)) {
            for (final PathRule rule : pathRuleSet) {
                if (rule.sourcePathPattern.matcher(path).matches()) {
                    if (rule.sourceTypePattern == null || (resource != null && rule.sourceTypePattern.matcher(
                            resource.getValueMap().get(JCR_PRIMARY_TYPE, "")).matches())) {
                        return rule;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void doGet(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        doIt(request, response, request.getParameterValues("path"));
    }

    @Override
    protected void doPost(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        doIt(request, response, request.getParameterValues("path"));
    }

    @Override
    protected void doPut(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response)
            throws IOException {
        doIt(request, response, IOUtils.readLines(request.getInputStream(), StandardCharsets.UTF_8).toArray(new String[0]));
    }

    protected void doIt(@NotNull final SlingHttpServletRequest request, @NotNull final SlingHttpServletResponse response,
                        final String... paths) throws IOException {
        try {
            final RequestPathInfo pathInfo = request.getRequestPathInfo();
            final String[] selectors = pathInfo.getSelectors();
            if (selectors.length > 1) {
                final Mode mode = Mode.valueOf(Optional.ofNullable(request.getParameter("mode")).orElse("merge").toUpperCase());
                final int levelMax = Integer.parseInt(Optional.ofNullable(request.getParameter("level")).orElse("4"));
                final ExtractSession session = new ExtractSession(request.getResourceResolver(), mode, levelMax);
                if (paths != null && paths.length > 0) {
                    session.scanExtractPaths(request.getResourceResolver(), paths);
                } else {
                    session.scanExtractPaths(request.getResource());
                }
                switch (selectors[1]) {
                    case "paths": {
                        pathsResponse(response, Optional.ofNullable(pathInfo.getExtension()).orElse("txt"), session, "source");
                    }
                    return;
                    case "scan": {
                        pathsResponse(response, Optional.ofNullable(pathInfo.getExtension()).orElse("txt"), session);
                    }
                    return;
                    case "copy": {
                        final boolean dryRun = Boolean.parseBoolean(Optional.ofNullable(request.getParameter("dryRun")).orElse("true"));
                        session.copyExtractPaths(dryRun);
                        if (!dryRun) {
                            request.getResourceResolver().commit();
                        }
                        if (selectors.length > 2 && "zip".equals(selectors[2])) {
                            response.setContentType("application/zip");
                            response.addHeader("Content-Disposition", "attachment; filename="
                                    + request.getResource().getName() + "-extract.zip");
                            session.writeArchive(response.getOutputStream());
                        } else {
                            pathsResponse(response, Optional.ofNullable(pathInfo.getExtension()).orElse("txt"), session);
                        }
                    }
                    return;
                }
            }
        } catch (Exception ex) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_GONE);
                response.setContentType("text/plain;charset=UTF-8");
                PrintWriter writer = response.getWriter();
                writer.println(ex.toString());
                ex.printStackTrace(writer);
            }
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    protected void pathsResponse(@NotNull final SlingHttpServletResponse response, @NotNull final String ext,
                                 @NotNull final ExtractSession session, final String... key) throws IOException {
        final PrintWriter writer = response.getWriter();
        switch (ext) {
            default:
            case "txt":
                prepareTextResponse(response, "text/plain");
                for (String name : key != null && key.length > 0 ? key : session.getPathSets().keySet().toArray(new String[0])) {
                    writePlainText(writer, name, session.getPathSets().get(name));
                }
                break;
            case "json":
                prepareTextResponse(response, "application/json");
                final JsonWriter jsonWriter = new JsonWriter(writer);
                jsonWriter.setIndent("  ");
                jsonWriter.beginObject();
                for (String name : key != null && key.length > 0 ? key : session.getPathSets().keySet().toArray(new String[0])) {
                    writeJson(jsonWriter, name, session.getPathSets().get(name));
                }
                jsonWriter.endObject();
                break;
        }
    }

    protected void writePlainText(@NotNull final PrintWriter writer, @Nullable final String name, @NotNull final Set<String> pathSet) {
        writer.println(name);
        for (final String path : pathSet) {
            writer.append("  ").println(path);
        }
    }

    protected void writeJson(@NotNull final JsonWriter writer, @Nullable final String name, @NotNull final Set<String> pathSet)
            throws IOException {
        writer.name(name).beginArray();
        for (final String path : pathSet) {
            writer.value(path);
        }
        writer.endArray();
    }

    protected void prepareTextResponse(@NotNull final HttpServletResponse response, @Nullable String contentType) {
        response.setHeader("Cache-Control", "no-cache");
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Cache-Control", "must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        contentType = StringUtils.defaultString(contentType, "text/plain");
        if (!contentType.contains("charset")) {
            contentType += ";charset=UTF-8";
        }
        response.setContentType(contentType);
    }

    //

    protected Map<String, Object> copyProperties(@NotNull final Resource source,
                                                 @NotNull final ResourceFilter filter, @Nullable final ValueMap toSkip) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.getValueMap().entrySet()) {
            final String key = entry.getKey();
            if (filter.isAllowedProperty(key) && (toSkip == null || !toSkip.containsKey(key))) {
                properties.put(key, entry.getValue());
            }
        }
        return properties;
    }

    protected boolean isAllowedChild(@NotNull final String path) {
        for (Pattern disabled : ignoredChildren) {
            if (disabled.matcher(path).matches()) {
                return false;
            }
        }
        return true;
    }

    protected boolean isAllowedProperty(@NotNull final String name) {
        for (Pattern disabled : ignoredProperties) {
            if (disabled.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }

    public static List<Pattern> patternList(@Nullable final String[] config) {
        List<Pattern> patterns = new ArrayList<>();
        if (config != null) {
            for (String rule : config) {
                if (StringUtils.isNotBlank(rule)) {
                    patterns.add(Pattern.compile(rule));
                }
            }
        }
        return patterns;
    }
}
