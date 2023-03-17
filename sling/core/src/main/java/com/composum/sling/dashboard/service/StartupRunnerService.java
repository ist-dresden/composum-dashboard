package com.composum.sling.dashboard.service;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.Map;

/**
 * a service definition to execute a configured set of groovy scripts automatically on servive activation
 * the execution is registered in a status resource, normally a script is executed only of its last modification
 * date is newer than the last execution of that script
 */
public interface StartupRunnerService {

    /**
     * the execution of all configured startup scripts (the automatic execution runned on service activation)
     */
    void runStartupStripts();

    /**
     * the execution of all configured startup scripts in a given context
     *
     * @param resolver  the resolver to use for script execution
     * @param variables additional binding variables, e.g. a 'dryRun' preset
     * @param output    the writer for the output printed by the scripts
     * @param force     if 'true' the scripts are executed always, not only if newer than on last execution
     */
    void runStartupStripts(@NotNull ResourceResolver resolver,
                           @NotNull Map<String, Object> variables,
                           @NotNull PrintWriter output, boolean force);

    /**
     * the execution on one of the startup srcipts, the script mustn't be listed for automatic execution,
     * but it must match the configured script patterns
     *
     * @param resolver   the resolver to use for script execution
     * @param scriptPath the path of the script to execute
     * @param variables  additional binding variables, e.g. a 'dryRun' preset
     * @param output     the writer for the output printed by the scripts
     * @param force      if 'true' the scripts are executed always, not only if newer than on last execution
     */
    void runStartupScript(@NotNull ResourceResolver resolver,
                          @NotNull String scriptPath, @NotNull Map<String, Object> variables,
                          @NotNull PrintWriter output, boolean force);

    /**
     * runs a grooy script provided by a reader in the context of the startup service configuration
     * this script mustn't match the startup script patterns, its execution is not registered
     *
     * @param resolver     the resolver to use for script execution
     * @param scriptReader the script content
     * @param variables    additional binding variables, e.g. a 'dryRun' preset
     * @param output       the writer for the output printed by the scripts
     * @param logger       the logger to use during script execution
     * @param name         the name of the script
     */
    void runGroovyScript(@NotNull ResourceResolver resolver,
                         @NotNull Reader scriptReader,
                         @NotNull Map<String, Object> variables,
                         @NotNull PrintWriter output,
                         @Nullable Logger logger,
                         @NotNull String name);

    /**
     * opens a reader to execute the script specified by the given path
     *
     * @param resolver    the resolver to use for script loading
     * @param classLoader the classloader used to load from bundle resources if the path start with 'resource:'
     * @param scriptPath  the path of the script to load
     * @return the reader to parse the script
     */
    @Nullable Reader openScript(@NotNull ResourceResolver resolver,
                                @NotNull ClassLoader classLoader,
                                @Nullable String scriptPath);

    /**
     * opens a reader to execute the script available as repository file resource
     *
     * @param resolver the resolver to use for script loading
     * @param resource the file resource
     * @return the reader to parse the script
     */
    @Nullable Reader openScript(@NotNull ResourceResolver resolver,
                                @Nullable Resource resource);
}
