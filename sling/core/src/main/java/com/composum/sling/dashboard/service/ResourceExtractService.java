package com.composum.sling.dashboard.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * a service to extract resources together with all referenced resources mapped
 * to a set of target resources (target paths) with all references adjusted to the target paths
 */
public interface ResourceExtractService {

    enum Mode {MERGE, UPDATE, REPLACE}

    /**
     * the implementation to build the extraction result of the given source mapped to the given target path
     */
    interface Extractor extends Closeable {

        /**
         * extract one resource mapped to the given target path
         *
         * @param source     the resource to extract
         * @param targetPath the path of the target of the extraction
         * @throws Exception if an error occurs
         */
        void extract(@NotNull Resource source, @NotNull String targetPath) throws Exception;
    }

    interface ExtractSession {

        /**
         * scans the given set of paths and refereced resources for a following extraction
         *
         * @param paths the paths set to scan
         */
        void scanExtractPaths(@NotNull String... paths);

        /**
         * scans the given set of resources and refereced resources for a following extraction
         *
         * @param resources the resource set to scan
         */
        void scanExtractPaths(@NotNull Resource... resources);

        /**
         * perfomrs the extraction of the previously scanned paths using the given extractor
         *
         * @param extractor the extrator implementation to use
         * @throws Exception if an error occurs
         */
        void extract(@NotNull Extractor extractor) throws Exception;

        /**
         * @return the various path sets (source, target, outside, missed) built during source scanning
         */
        Map<String, Set<String>> getPathSets();
    }

    @NotNull ExtractSession createSession(@Nullable ResourceExtractConfig config,
                                          @NotNull ResourceResolver resolver, int levelMax);

    /**
     * creates an extractor to copy source resources to the mapping target paths
     * in the same repository with reference adjustment
     *
     * @param config  the configuration to use, 'null': the default configuration of the service is used
     * @param mode    the mode to control how properties copy to existing target resources are handled
     * @param dryRun  if 'true' nothing is persistent changed, the affected paths are logged
     * @param session the extract session to control the execution
     * @return the copy extractor
     */
    @NotNull Extractor createCopyExtractor(@Nullable ResourceExtractConfig config,
                                           @Nullable Mode mode, boolean dryRun,
                                           @NotNull ExtractSession session);

    /**
     * creates an extractor to extract the source resources into a ZIP file of XML sourcecode entries
     * with or without mapping to the target paths and with reference adjustment if paths are mapped
     *
     * @param config       the configuration to use, 'null': the default configuration of the service is used
     * @param targetFilter an additional filter to specify the content of interest that should be stored in the ZIP
     * @param mapToTarget  if 'false' the sources are not mapped to the configured target paths
     * @param session      the extract session to control the execution
     * @param outputStream the output stream to store the generated ZIP file
     * @return the zip extractor
     */
    @NotNull Extractor createZipExtractor(@Nullable ResourceExtractConfig config,
                                          @Nullable Pattern targetFilter,
                                          @Nullable Boolean mapToTarget,
                                          @NotNull ExtractSession session,
                                          @NotNull OutputStream outputStream);

    /**
     * creates an extractor to extract the source resources into one JSON file (e.g. as unit test source)
     * with or without mapping to the target paths and with reference adjustment if paths are mapped
     *
     * @param config       the configuration to use, 'null': the default configuration of the service is used
     * @param targetFilter an additional filter to specify the content of interest that should be stored in the JSON output
     * @param mapToTarget  if 'false' the sources are not mapped to the configured target paths
     * @param session      the extract session to control the execution
     * @param outputStream the output stream to store the generated JSON output
     * @return the JSON extractor
     */
    @NotNull Extractor createJsonExtractor(@Nullable ResourceExtractConfig config,
                                           @Nullable Pattern targetFilter,
                                           @Nullable Boolean mapToTarget,
                                           @NotNull ExtractSession session,
                                           @NotNull OutputStream outputStream);

    /**
     * the mapping configuration to map source paths to target paths during the content extraction,
     * a set of up to four mapping entries separated by ',' according to:
     * <ol>
     *     <li>a regex pattern to determine which source paths this rule should be applied to, e.g.
     *     '^/content/site/source(/.*)?$'
     *     </li>
     *     <li>a node type pattern as a second filter criteria, '' or '*' for each node type</li>
     *     <li>the max depth of the content copy for that rule, e.g.
     *     '1' for the resource itself and the whole 'jcr:content' child if present (pages or assets);
     *     '*' or '' for the whole content of the matching resource (unstructured data)</li>
     *     <li>the optional replace pattern to build the target path of the source path pattern, e.g.
     *     '/content/site/target$1' to map each '/content/site/source/...' to ''/content/site/target/...'
     *     </li>
     * </ol>
     * example configuration:
     * <ul>
     *     <li>/content/(site)/[^/]+(/.+),cq:Page,1,/content/test/reference$2</li>
     *     <li>/content/(test)(/.+),cq:Page,1</li>
     *     <li>/content/dam(/[^/]+(/.+)),dam:Asset,1,/content/dam/test/reference$2</li>
     *     <li>/var/commerce/products/data(/.+),(nt|sling):[Ff]older,1,/var/commerce/products/test$1</li>
     *     <li>/var/commerce/products/data(/.+),nt:unstructured,*,/var/commerce/products/test$1</li>
     * </ul>
     */
    class PathMappingRule {

        public final Pattern sourcePathPattern;
        public final Pattern sourceTypePattern;
        public final Integer maxDepth;
        public final String targetPathPattern;

        public PathMappingRule(@NotNull final String expression) {
            final String[] parts = StringUtils.split(expression, ",", 4);
            sourcePathPattern = Pattern.compile(parts[0]);
            sourceTypePattern = parts.length > 1 && StringUtils.isNotBlank(parts[1]) &&
                    !"*".equals(parts[1]) ? Pattern.compile(parts[1]) : null;
            maxDepth = parts.length > 2 && StringUtils.isNotBlank(parts[2]) &&
                    !"*".equals(parts[2]) ? Integer.parseInt(parts[2]) : null;
            targetPathPattern = parts.length > 3 && StringUtils.isNotBlank(parts[3]) ? parts[3] : null;
        }
    }
}
