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

    interface Extractor extends Closeable {

        void extract(@NotNull Resource source, @NotNull String targetPath) throws Exception;
    }

    interface ExtractSession {

        void scanExtractPaths(@NotNull String... paths);

        void scanExtractPaths(@NotNull Resource... target);

        void extract(@NotNull Extractor extractor) throws Exception;

        Map<String, Set<String>> getPathSets();
    }

    @NotNull ExtractSession createSession(@Nullable ResourceExtractConfig config,
                                          @NotNull ResourceResolver resolver, boolean dryRun,
                                          @Nullable Mode mode, int levelMax);

    @NotNull Extractor createCopyExtractor(@Nullable ResourceExtractConfig config,
                                           @NotNull ExtractSession session);

    @NotNull Extractor createZipExtractor(@Nullable final ResourceExtractConfig config,
                                          @NotNull ExtractSession session,
                                          @NotNull OutputStream outputStream);

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
