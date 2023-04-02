package com.composum.sling.dashboard.service;

import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.composum.sling.dashboard.DashboardConfig.JCR_CONTENT;
import static com.composum.sling.dashboard.DashboardConfig.JCR_CREATED;
import static com.composum.sling.dashboard.DashboardConfig.JCR_DATA;
import static com.composum.sling.dashboard.DashboardConfig.JCR_LAST_MODIFIED;
import static com.composum.sling.dashboard.DashboardConfig.JCR_MIME_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.JCR_MIXIN_TYPES;
import static com.composum.sling.dashboard.DashboardConfig.JCR_PRIMARY_TYPE;
import static com.composum.sling.dashboard.DashboardConfig.NT_FILE;
import static com.composum.sling.dashboard.DashboardConfig.NT_RESOURCE;
import static com.composum.sling.dashboard.DashboardConfig.NT_UNSTRUCTURED;
import static com.composum.sling.dashboard.DashboardConfig.patternList;

@Component
@Designate(ocd = ResourceExtractConfig.class)
public class DashboardExtractService implements ResourceExtractService {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardExtractService.class);

    /**
     * the extractor to generate a ZIP file containing the source resources as XML sourcecode files
     * mapped to the target paths of the source resources
     */
    public class SourceZipExtractor implements Extractor {

        protected final Pattern targetFilter;
        protected final boolean mapToTarget;
        protected final DashboardExtractSession session;
        protected final ZipOutputStream zipStream;
        protected Map<Pattern, List<String>> additionalZipEntries;

        private final Set<String> entryResourceSet = new TreeSet<>();

        public SourceZipExtractor(@NotNull final ResourceExtractConfig config,
                                  @Nullable final Pattern targetFilter,
                                  @Nullable final Boolean mapToTarget,
                                  @NotNull final ExtractSession session,
                                  @NotNull final OutputStream outputStream) {
            this.targetFilter = targetFilter;
            this.mapToTarget = mapToTarget == null || mapToTarget;
            this.session = (DashboardExtractSession) session;
            this.zipStream = new ZipOutputStream(outputStream);
            additionalZipEntries = new LinkedHashMap<>();
            for (final String expression : config.addZipEntries()) {
                if (StringUtils.isNotBlank(expression)) {
                    String[] parts = StringUtils.split(expression, ",");
                    additionalZipEntries.put(Pattern.compile(parts[0]),
                            Arrays.stream(parts).skip(1).collect(Collectors.toList()));
                }
            }
        }

        @Override
        public void close() throws IOException {
            zipStream.flush();
            zipStream.close();
        }

        @Override
        public void extract(@NotNull final Resource source, @NotNull final String targetPath)
                throws IOException {
            if (xmlRenderer != null) {
                extract(source, targetPath, true);
            }
        }

        /**
         * extract a source resource any maybe its parents and configured child elements
         *
         * @param source       the source resource to extract
         * @param targetPath   the target path to extract to (the path of the zip entry)
         * @param writeParents specifies whether the content of the parents should be written or not
         * @throws IOException if an error occurs
         */
        protected void extract(@Nullable final Resource source, @NotNull String targetPath, boolean writeParents)
                throws IOException {
            if (!mapToTarget && source != null) {
                targetPath = source.getPath();
            }
            if (source == null || ResourceUtil.isNonExistingResource(source) || !session.isAllowedResource(source)
                    || (targetFilter != null && !targetFilter.matcher(targetPath).matches())) {
                return;
            }
            final String sourcePath = source.getPath();
            Resource sourceParent = source;
            String targetParentPath = targetPath;
            while ((sourceParent = sourceParent.getParent()) != null
                    && StringUtils.countMatches(targetParentPath, '/') > 1
                    && StringUtils.isNotBlank(targetParentPath
                    = StringUtils.substringBeforeLast(targetParentPath, "/"))) {
                extractResource(sourceParent, targetParentPath, writeParents);
            }
            extractResource(source, targetPath, true);
            final ValueMap properties = source.getValueMap();
            for (final Map.Entry<Pattern, List<String>> entry : additionalZipEntries.entrySet()) {
                final Pattern pattern = entry.getKey();
                String additionalPath;
                Matcher matcher;
                if (pattern.matcher(properties.get(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED)).matches()) {
                    for (final String pathRule : entry.getValue()) {
                        additionalPath = pathRule.startsWith("/") ? pathRule : "/" + pathRule;
                        extract(session.resolver.getResource(sourcePath + additionalPath),
                                targetPath + additionalPath, false);
                    }
                } else if ((matcher = pattern.matcher(sourcePath)).matches()) {
                    for (final String pathRule : entry.getValue()) {
                        additionalPath = matcher.replaceFirst(pathRule);
                        extract(session.resolver.getResource(sourcePath + additionalPath),
                                targetPath + additionalPath, false);
                    }
                }
            }
        }

        /**
         * extract on single resource as XML source file added to the ZIP stream if not already added
         *
         * @param source       the source resource to extract
         * @param targetPath   the target path to extract to (the path of the zip entry)
         * @param writeContent if 'true' an XML content file is created and added to the ZIP stream
         * @throws IOException if an error occurs
         */
        public void extractResource(@NotNull final Resource source,
                                    @NotNull final String targetPath, boolean writeContent)
                throws IOException {
            if (entryResourceSet.contains(targetPath)) {
                return; // skip this resource if already added to the ZIP
            }
            entryResourceSet.add(targetPath);
            final ValueMap properties = source.getValueMap();
            final String primaryType = properties.get(JCR_PRIMARY_TYPE, "");
            if (NT_RESOURCE.equals(primaryType)) {
                return; // such resources are part of the parent file resource
            }
            final String zipName = targetPath.replaceAll("/jcr:content(/.+)?", "/_jcr_content$1");
            ZipEntry entry;
            if (NT_FILE.equals(primaryType)) {
                final Resource content = source.getChild(JCR_CONTENT);
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
                            setLastModified(entry, source);
                            zipStream.putNextEntry(entry);
                            writeXmlToZip(source, 1);
                            zipStream.closeEntry();
                        }
                        entry = new ZipEntry(zipName);
                        setLastModified(entry, source);
                        zipStream.putNextEntry(entry);
                        IOUtils.copy(data, zipStream);
                        zipStream.closeEntry();
                    }
                }
            } else if (writeContent && StringUtils.isNotBlank(primaryType)) {
                final boolean isFolder = primaryType.matches("^(sling|nt):(Ordered)?[Ff]older$");
                final boolean hasContent = source.getChild(JCR_CONTENT) != null;
                entry = new ZipEntry(zipName + "/.content.xml");
                setLastModified(entry, source);
                zipStream.putNextEntry(entry);
                writeXmlToZip(source, isFolder || hasContent ? 1 : null);
                zipStream.closeEntry();
            }
        }

        /**
         * writes the content as XML source using the general XmlRenderer service of the dashboard
         *
         * @param resource the resource to write as XML source file
         * @param maxDepth the max depth of the rendered content; 'null' = infinite
         */
        protected void writeXmlToZip(@NotNull final Resource resource, @Nullable final Integer maxDepth) {
            try {
                final PrintWriter writer = new PrintWriter(zipStream);
                xmlRenderer.dumpXml(writer, "", resource, 0, maxDepth,
                        session, session::isAllowedProperty, xmlRenderer::isAllowedMixin,
                        mapToTarget ? session::adjustProperty : null);
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

    /**
     * the extractor to generate a ZIP file containing the source resources as XML sourcecode files
     * mapped to the target paths of the source resources
     */
    public class SourceJsonExtractor implements Extractor {

        protected final Pattern targetFilter;
        protected final boolean mapToTarget;
        public final DashboardExtractSession session;
        protected final JsonWriter jsonWriter;
        protected final Stack<String> openPath = new Stack<>();

        private final Set<String> entryResourceSet = new TreeSet<>();

        public SourceJsonExtractor(@NotNull final ResourceExtractConfig config,
                                   @Nullable final Pattern targetFilter,
                                   @Nullable final Boolean mapToTarget,
                                   @NotNull final ExtractSession session,
                                   @NotNull final OutputStream outputStream) {
            this.targetFilter = targetFilter;
            this.mapToTarget = mapToTarget == null || mapToTarget;
            this.session = (DashboardExtractSession) session;
            this.jsonWriter = new JsonWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            while (!openPath.isEmpty()) {
                openPath.pop();
                jsonWriter.endObject();
            }
            jsonWriter.flush();
            jsonWriter.close();
        }

        @Override
        public void extract(@Nullable final Resource source, @NotNull String targetPath)
                throws IOException {
            if (!mapToTarget && source != null) {
                targetPath = source.getPath();
            }
            if (jsonRenderer == null
                    || (source != null && (ResourceUtil.isNonExistingResource(source)
                    || !session.isAllowedResource(source)))
                    || (targetFilter != null && !targetFilter.matcher(targetPath).matches())) {
                return;
            }
            String targetParentPath = StringUtils.substringBeforeLast(targetPath, "/");
            while (!openPath.isEmpty() && StringUtils.isNotBlank(targetParentPath)
                    && !targetParentPath.startsWith(openPath.peek())) {
                jsonWriter.endObject();
                openPath.pop();
            }
            if (!"/".equals(targetPath) && !entryResourceSet.contains(targetParentPath)) {
                extract(source != null ? source.getParent() : null,
                        StringUtils.isBlank(targetParentPath) ? "/" : targetParentPath);
            }
            extractResource(source, StringUtils.isBlank(targetPath) ? "/" : targetPath);
        }

        /**
         * extract on single resource as XML source file added to the ZIP stream if not already added
         *
         * @param source     the source resource to extract
         * @param targetPath the target path to extract to (the path of the zip entry)
         * @throws IOException if an error occurs
         */
        public void extractResource(@Nullable final Resource source, @NotNull final String targetPath)
                throws IOException {
            if (entryResourceSet.contains(targetPath)) {
                return; // skip this resource if already added to the ZIP
            }
            entryResourceSet.add(targetPath);
            final String jsonName = StringUtils.substringAfterLast(targetPath, "/");
            if (!openPath.isEmpty()) {
                jsonWriter.name(jsonName).beginObject();
            } else {
                jsonWriter.beginObject();
            }
            openPath.push(targetPath);
            if (source != null && !"jcr:content".equals(source.getName())) {
                final PathMappingRule pathMappingRule = session.getPathRule(source.getPath(), source);
                writeJsonSource(source, pathMappingRule != null ? pathMappingRule.maxDepth : (Integer) 1);
            }
        }

        /**
         * writes the content as JSON source content
         *
         * @param resource the resource to write as XML source file1
         * @param maxDepth the maximum depth if the json output, 'null': infinite
         */
        protected void writeJsonSource(@NotNull final Resource resource, @Nullable final Integer maxDepth) {
            try {
                jsonRenderer.dumpJsonContent(jsonWriter, resource, 0, maxDepth,
                        session, session::isAllowedProperty, xmlRenderer::isAllowedMixin,
                        mapToTarget ? session::adjustProperty : null);
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * the extractor to generate an isolated cope of the source at the target destination
     */
    protected class CopyToTargetExtractor implements Extractor {

        public final Mode mode;
        public final boolean dryRun;
        public final DashboardExtractSession session;

        public CopyToTargetExtractor(@Nullable final Mode mode, boolean dryRun,
                                     @NotNull final ExtractSession session) {
            this.mode = mode != null ? mode : Mode.MERGE;
            this.dryRun = dryRun;
            this.session = (DashboardExtractSession) session;
        }

        @Override
        public void close() throws IOException {
            if (!dryRun) {
                session.resolver.commit();
            }
        }

        @Override
        public void extract(@NotNull Resource source, @NotNull String targetPath)
                throws PersistenceException {
            final Resource parent = provideParent(source, targetPath);
            copyResource(source, parent, source.getName(), 0);
        }

        protected Resource copyResource(@NotNull final Resource source,
                                        @NotNull final Resource parent, @NotNull final String name, final int depth)
                throws PersistenceException {
            Resource target = parent.getChild(name);
            if (target == null || mode == Mode.REPLACE) {
                if (target != null) {
                    session.resolver.delete(target);
                }
                LOG.info("copyResource({},{},{}).create", source.getPath(), parent.getPath(), name);
                target = session.resolver.create(parent, name,
                        session.copyProperties(source, null, session::adjustProperty));
            } else {
                final ModifiableValueMap targetProps = target.adaptTo(ModifiableValueMap.class);
                if (targetProps != null) {
                    targetProps.putAll(session.copyProperties(source,
                            mode == Mode.UPDATE ? null : targetProps,
                            session::adjustProperty));
                }
            }
            for (final Resource child : source.getChildren()) {
                if (session.isAllowedResource(child)) {
                    final PathMappingRule pathMappingRule = session.getPathRule(source.getPath(), source);
                    if (child.getPath().matches("^.*/" + JCR_CONTENT + "(/.*)?$")
                            || (pathMappingRule != null
                            && (pathMappingRule.maxDepth == null || depth + 1 < pathMappingRule.maxDepth))) {
                        copyResource(child, target, child.getName(), depth + 1);
                    }
                }
            }
            return target;
        }

        protected @NotNull Resource provideParent(@NotNull final Resource source, @NotNull final String targetPath)
                throws PersistenceException {
            final String parentPath = StringUtils.substringBeforeLast(targetPath, "/");
            Resource parent = session.resolver.getResource(parentPath);
            if (parent == null && !targetPath.equals(parentPath)) {
                final Resource sourceParent = source.getParent();
                if (sourceParent != null) {
                    final Resource parentParent = provideParent(sourceParent, parentPath);
                    final String parentName = StringUtils.substringAfterLast(parentPath, "/");
                    LOG.info("provideParent({},{}).create({},{})",
                            source.getPath(), targetPath, parentParent.getPath(), parentName);
                    parent = session.resolver.create(parentParent, parentName,
                            session.copyProperties(sourceParent, null, session::adjustProperty));
                    final Resource sourceParentContent = sourceParent.getChild(JCR_CONTENT);
                    if (sourceParentContent != null) {
                        copyResource(sourceParentContent, parent, JCR_CONTENT, 0);
                    }
                }
            }
            if (parent == null) {
                throw new PersistenceException("can't create parent (" + targetPath + ")");
            }
            return parent;
        }
    }

    protected class DashboardExtractSession implements ExtractSession, ResourceFilter {

        public final String[] predefinedPaths;
        public final List<PathMappingRule> pathMappingRuleSet;
        protected List<Pattern> ignoredChildren;
        protected List<Pattern> ignoredProperties;

        public final ResourceResolver resolver;
        public final int levelMax;
        public final TreeSet<String> sourcePathSet;
        public final TreeSet<String> targetPathSet;
        public final Map<String, String> sourceToTarget;
        public final TreeSet<String> outsidePaths;
        public final TreeSet<String> missedPaths;
        public final Map<String, Set<String>> pathSets;

        private DashboardExtractSession(@NotNull final ResourceExtractConfig config,
                                        @NotNull final ResourceResolver resolver,
                                        final int levelMax) {
            predefinedPaths = config.predefinedPaths();
            pathMappingRuleSet = new ArrayList<>();
            for (final String expression : config.pathRuleSet()) {
                if (StringUtils.isNotBlank(expression)) {
                    pathMappingRuleSet.add(new PathMappingRule(expression));
                }
            }
            ignoredChildren = patternList(config.ignoredChildren());
            ignoredProperties = patternList(config.ignoredProperties());
            this.resolver = resolver;
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

        public void extract(@NotNull final Extractor extractor)
                throws Exception {
            for (final String sourcePath : getSourcePathSet()) {
                final Resource source = getResource(sourcePath);
                if (source != null) {
                    final String targetPath = sourceToTarget.get(sourcePath);
                    if (StringUtils.isNotBlank(targetPath) && !targetPath.equals(sourcePath)) {
                        extractor.extract(source, targetPath);
                    }
                }
            }
        }

        protected @Nullable Resource getResource(@Nullable final String path) {
            return StringUtils.isNotBlank(path) ? resolver.getResource(path) : null;
        }

        // scan

        protected @Nullable PathMappingRule getPathRule(@Nullable final String path,
                                                        @Nullable final Resource resource) {
            if (StringUtils.isNotBlank(path)) {
                for (final PathMappingRule rule : pathMappingRuleSet) {
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

        public void scanExtractPaths(@NotNull final String... paths) {
            for (final String path : paths) {
                if ("pre".equals(path) || "predefined".equals(path)) {
                    if (predefinedPaths != null) {
                        scanExtractPaths(predefinedPaths);
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

        /**
         * @return the first matching path mapping rule of an already registered parent upwards in the hierarchy
         */
        protected @Nullable PathMappingRule getRegisteredParentRule(@NotNull final Resource source) {
            final Resource sourceParent = source.getParent();
            final String sourceParentPath;
            if (sourceParent != null
                    && sourcePathSet.contains(sourceParentPath = sourceParent.getPath())) {
                final PathMappingRule parentMappingRule = getPathRule(sourceParentPath, sourceParent);
                return parentMappingRule != null ? parentMappingRule : getRegisteredParentRule(sourceParent);
            }
            return null;
        }

        protected void registerReferenceCandidate(@NotNull final String sourcePath, int level) {
            if (level < levelMax && !sourcePathSet.contains(sourcePath)) {
                final Resource source = resolver.getResource(sourcePath);
                if (source != null) {
                    final PathMappingRule pathMappingRule = getPathRule(sourcePath, source);
                    if (pathMappingRule != null) {
                        final PathMappingRule parentMappingRule = getRegisteredParentRule(source);
                        // if there is a parent mapping rule of a registered parent resource that's
                        // determining that all content should be embedded in that parent this source
                        // should not be exported also, it's already inlcuded in the parent but maybe
                        // found as a reference and therefore scanned separately here
                        boolean registerPath = parentMappingRule == null || parentMappingRule.maxDepth != null;
                        if (registerPath) {
                            sourcePathSet.add(sourcePath);
                        }
                        if (StringUtils.isNotBlank(pathMappingRule.targetPathPattern)) {
                            final Matcher sourceMatcher = pathMappingRule.sourcePathPattern.matcher(sourcePath);
                            if (sourceMatcher.matches()) {
                                final String targetPath = sourceMatcher.replaceFirst(pathMappingRule.targetPathPattern);
                                if (StringUtils.isNotBlank(targetPath) && !targetPath.equals(sourcePath)) {
                                    sourceToTarget.put(sourcePath, targetPath);
                                    if (registerPath) {
                                        targetPathSet.add(targetPath);
                                    }
                                }
                            }
                        }
                        collectNeededResources(level + 1, source, 0, pathMappingRule.maxDepth);
                    } else {
                        outsidePaths.add(sourcePath);
                    }
                    registerParentReferences(source);
                } else {
                    missedPaths.add(sourcePath);
                }
            }
        }

        protected void registerParentReferences(@NotNull final Resource resource) {
            final Resource parent = resource.getParent();
            if (parent != null && !sourcePathSet.contains(parent.getPath())) {
                final PathMappingRule parentRule = getPathRule(parent.getPath(), parent);
                if (parentRule != null) {
                    sourcePathSet.add(parent.getPath());
                    collectNeededResources(0, parent, 0, parentRule.maxDepth);
                    registerParentReferences(parent);
                }
            }
        }

        protected void collectNeededResources(int level, @NotNull final Resource target,
                                              int depth, @Nullable Integer maxDepth) {
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
                if (PATH_PATTERN.matcher(value).matches()) {
                    registerReferenceCandidate(value, level);
                } else {
                    final Matcher matcher = PATH_EMBEDDED.matcher(value);
                    final int length = value.length();
                    int pos = 0;
                    while (pos < length && matcher.find(pos)) {
                        registerReferenceCandidate(matcher.group("path"), level);
                        pos = matcher.end() + 1;
                    }
                }
            }
        }

        /**
         * the path property value transformer
         *
         * @param value the value to transform if it contains paths
         * @return the transformed value
         */
        protected @Nullable Object adjustProperty(@Nullable final Object value) {
            final Object changed = value != null ? adjustPathProperty(value) : null;
            return changed != null ? changed : value;
        }

        protected @Nullable Object adjustPathProperty(@NotNull final Object value) {
            if (value instanceof String) {
                return adjustPathString((String) value);
            } else if (value instanceof String[]) {
                final String[] multi = (String[]) value;
                boolean modified = false;
                for (int i = 0; i < multi.length; i++) {
                    final String chngd = adjustPathString(multi[i]);
                    if (chngd != null) {
                        multi[i] = chngd;
                        modified = true;
                    }
                }
                if (modified) {
                    return multi;
                }
            }
            return null; // nothing changed
        }

        protected @Nullable String adjustPathString(@NotNull final String value) {
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

        protected Map<String, Object> copyProperties(@NotNull final Resource source, @Nullable final ValueMap toSkip,
                                                     @Nullable final Function<Object, Object> transformer) {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.getValueMap().entrySet()) {
                final String key = entry.getKey();
                if (isAllowedProperty(key) && (toSkip == null || !toSkip.containsKey(key))) {
                    Object value = entry.getValue();
                    properties.put(key, transformer != null ? transformer.apply(value) : value);
                }
            }
            return properties;
        }

        @Override
        public boolean isAllowedResource(@NotNull final Resource resource) {
            if (resourceFilter != null && !resourceFilter.isAllowedResource(resource)) {
                return false;
            }
            for (Pattern disabled : ignoredChildren) {
                if (disabled.matcher(resource.getPath()).matches()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isAllowedProperty(@NotNull final String name) {
            if (resourceFilter != null && !resourceFilter.isAllowedProperty(name)) {
                return false;
            }
            for (Pattern disabled : ignoredProperties) {
                if (disabled.matcher(name).matches()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public @Nullable Resource getRequestResource(@NotNull final SlingHttpServletRequest request) {
            return resourceFilter != null ? resourceFilter.getRequestResource(request) : request.getResource();
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private ResourceFilter resourceFilter;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private XmlRenderer xmlRenderer;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private JsonRenderer jsonRenderer;

    protected ResourceExtractConfig defaultConfig;

    @Activate
    @Modified
    protected void activate(ResourceExtractConfig config) {
        defaultConfig = config;
    }

    @Override
    public @NotNull ExtractSession createSession(@Nullable final ResourceExtractConfig config,
                                                 @NotNull final ResourceResolver resolver,
                                                 final int levelMax) {
        return new DashboardExtractSession(config != null ? config : defaultConfig,
                resolver, levelMax);
    }

    public @NotNull Extractor createCopyExtractor(@Nullable final ResourceExtractConfig config,
                                                  @Nullable final Mode mode, boolean dryRun,
                                                  @NotNull final ExtractSession session) {
        return new CopyToTargetExtractor(mode, dryRun, session);
    }

    public @NotNull Extractor createZipExtractor(@Nullable final ResourceExtractConfig config,
                                                 @Nullable final Pattern targetFilter,
                                                 @Nullable final Boolean mapToTarget,
                                                 @NotNull final ExtractSession session,
                                                 @NotNull final OutputStream outputStream) {
        return new SourceZipExtractor(config != null ? config : defaultConfig,
                targetFilter, mapToTarget, session, outputStream);
    }

    public @NotNull Extractor createJsonExtractor(@Nullable final ResourceExtractConfig config,
                                                  @Nullable final Pattern targetFilter,
                                                  @Nullable final Boolean mapToTarget,
                                                  @NotNull final ExtractSession session,
                                                  @NotNull final OutputStream outputStream) {
        return new SourceJsonExtractor(config != null ? config : defaultConfig,
                targetFilter, mapToTarget, session, outputStream);
    }
}
