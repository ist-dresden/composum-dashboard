package com.composum.sling.dashboard.service;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import groovy.transform.TimedInterrupt;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.adapter.Adaptable;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.query.QueryManager;
import javax.servlet.Servlet;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.dashboard.service.StartupRunnerService.MODE.DEPLOYED;
import static com.composum.sling.dashboard.service.StartupRunnerService.MODE.MODIFIED;
import static org.osgi.framework.Bundle.ACTIVE;

import com.composum.sling.dashboard.servlet.ConfigurationConstants;

@Component(
        service = {StartupRunnerService.class, Servlet.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DashboardStartupService.Config.class)
public class DashboardStartupService extends SlingSafeMethodsServlet implements StartupRunnerService {

    public static final String LAST_EXECUTED = "lastExecuted";
    public static final String JCR_LAST_MODIFIED = "jcr:lastModified";
    public static final String EXECUTIONS = "executions";
    public static final String JCR_CONTENT = "jcr:content";
    public static final String JCR_DATA = "jcr:data";
    public static final String FILE_MODIFIED = JCR_CONTENT + "/" + JCR_LAST_MODIFIED;
    public static final String FILE_DATA = JCR_CONTENT + "/" + JCR_DATA;
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String NT_UNSTRUCTURED = "nt:unstructured";
    public static final String NT_FILE = "nt:file";

    public static final Map<String, Object> SERVICE_AUTH
            = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "startup");

    public static final String _NODE_BUILDER = ".aem.groovy.extension.builders.NodeBuilder";
    public static final String _PAGE_BUILDER = ".aem.groovy.extension.builders.PageBuilder";

    @ObjectClassDefinition(name = "Composum Sling Dashboard Startup Service")
    public @interface Config {

        @AttributeDefinition(
                name = "Dry Run",
                description = "declares the value of the binding variable 'dryRun' (startup mode only)"
        )
        boolean dryRun() default true;

        @AttributeDefinition(
                name = "Force",
                description = "if 'true' / 'on' the scripts are always executed (startup mode only)"
        )
        boolean force() default false;

        @AttributeDefinition(
                name = "Run Once",
                description = "the list of script paths that should be executed only once after script update"
        )
        String[] runOnce();

        @AttributeDefinition(
                name = "Run On Deployment",
                description = "the list of script paths that should be executed after each deployment"
        )
        String[] runOnDeployment();

        @AttributeDefinition(
                name = "Script Path Pattern",
                description = "the regex replace pattern to map the allowed script paths to status paths"
        )
        String[] scriptPathPattern() default {
                "^/conf/global/startup/script(/.+)$=/var/composum/dashboard/startup/status$1"
        };

        @AttributeDefinition(
                name = "Star Imports",
                description = "the list of script paths that should be executed only once after script update"
        )
        String[] starImports() default {
                "javax.jcr",
                "org.apache.sling.api",
                "org.apache.sling.api.resource"
        };

        @AttributeDefinition(
                name = "Setup Scripts",
                description = "the script to initiaize the meta class"
        )
        String[] setupScripts() default {
        };

        @AttributeDefinition(
                name = "Thread Timeout"
        )
        long threadTimeout() default (60L * 60L * 1000L);

        @AttributeDefinition(name = ConfigurationConstants.CFG_RESOURCE_TYPE_NAME,
                description = ConfigurationConstants.CFG_RESOURCE_TYPE_DESCRIPTION)
        String[] sling_servlet_resourceTypes();

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_EXTENSIONS_DESCRIPTION)
        String[] sling_servlet_extensions() default {
                "txt"
        };

        @AttributeDefinition(name = ConfigurationConstants.CFG_SERVLET_PATHS_NAME,
                description = ConfigurationConstants.CFG_SERVLET_PATHS_DESCRIPTION)
        String[] sling_servlet_paths();
    }

    private static final Logger LOG = LoggerFactory.getLogger(DashboardStartupService.class);

    @Reference
    private DynamicClassLoaderManager dynamicClassLoaderManager;

    @Reference
    private ResourceResolverFactory resolverFactory;

    protected BundleContext bundleContext;
    protected Config config;

    protected Map<Pattern, String> scriptPathPattern = new LinkedHashMap<>();
    protected String statusPathPattern;

    @Activate
    protected void activate(final BundleContext bundleContext, final Config config) {
        this.bundleContext = bundleContext;
        this.config = config;
        this.scriptPathPattern.clear();
        for (final String statusPathRule : config.scriptPathPattern()) {
            this.scriptPathPattern.put(
                    Pattern.compile(StringUtils.substringBefore(statusPathRule, "=")),
                    StringUtils.substringAfter(statusPathRule, "="));
        }
        new Thread(this::runStartupStripts).start();
    }

    protected Calendar getLastDeployed() {
        final Calendar result = Calendar.getInstance();
        result.setTimeInMillis(bundleContext.getBundle().getLastModified());
        return result;
    }

    @Override
    public void runStartupStripts() {
        waitForAllBundlesActive();
        try (final ResourceResolver resolver = resolverFactory.getServiceResourceResolver(SERVICE_AUTH)) {
            runStartupStripts(resolver, null, Collections.singletonMap("dryRun",
                    config.dryRun()), new DropIt(), config.force());
        } catch (LoginException ex) {
            LOG.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void runStartupStripts(@NotNull final ResourceResolver resolver, @Nullable final MODE mode,
                                  @NotNull final Map<String, Object> variables,
                                  @NotNull final PrintWriter output, boolean force) {
        if (mode == null || mode == MODIFIED) {
            for (final String scriptPath : config.runOnce()) {
                output.append(scriptPath).append("...\n");
                runStartupScript(resolver, MODIFIED, scriptPath, variables, output, force);
                output.append(scriptPath).append(".\n");
            }
        }
        if (mode == null || mode == DEPLOYED) {
            for (final String scriptPath : config.runOnDeployment()) {
                output.append(scriptPath).append("...\n");
                runStartupScript(resolver, DEPLOYED, scriptPath, variables, output, force);
                output.append(scriptPath).append(".\n");
            }
        }
    }

    @Override
    public void doGet(@NotNull final SlingHttpServletRequest request,
                      @NotNull final SlingHttpServletResponse response)
            throws IOException {
        final RequestPathInfo pathInfo = request.getRequestPathInfo();
        final ResourceResolver resolver = request.getResourceResolver();
        final boolean force = Optional.ofNullable(request.getParameter("force"))
                .map(Boolean::parseBoolean).orElse(false);
        final boolean dryRun = Optional.ofNullable(request.getParameter("dryRun"))
                .map(Boolean::parseBoolean).orElse(true);
        response.setContentType("text/plain,charset=UTF-8");
        final PrintWriter writer = response.getWriter();
        final String scriptPath = pathInfo.getSuffix();
        if (StringUtils.isNotBlank(scriptPath)) {
            final MODE mode = DEPLOYED.name().equals(Optional.ofNullable(request.getParameter("mode"))
                    .orElse(List.of(config.runOnce()).contains(scriptPath) ? "modified" : "deployed")
                    .toUpperCase()) ? DEPLOYED : MODIFIED;
            runStartupScript(resolver, mode, scriptPath, Collections.singletonMap("dryRun", dryRun), writer, force);
        } else {
            final MODE mode = Optional.ofNullable(request.getParameter("mode"))
                    .map(p -> {
                        try {
                            return MODE.valueOf(p.toUpperCase());
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .orElse(null);
            runStartupStripts(resolver, mode, Collections.singletonMap("dryRun", dryRun), writer, force);
        }
        writer.flush();
    }

    @Override
    public void runStartupScript(@NotNull final ResourceResolver resolver, @NotNull final MODE mode,
                                 @NotNull final String scriptPath, @NotNull final Map<String, Object> variables,
                                 @NotNull final PrintWriter output, boolean force) {
        Matcher matcher = null;
        String statusPathPattern = null;
        for (Map.Entry<Pattern, String> rule : scriptPathPattern.entrySet()) {
            matcher = rule.getKey().matcher(scriptPath);
            if (matcher.matches()) {
                statusPathPattern = rule.getValue();
                break;
            }
        }
        if (matcher != null && matcher.matches() && statusPathPattern != null) {
            final String statusPath = matcher.replaceFirst(statusPathPattern);
            try {
                runStartupScript(resolver, mode, scriptPath, variables, output, statusPath, force);
            } catch (PersistenceException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } else {
            LOG.error("script path '{}' doesn't match the path pattern '{}'", scriptPath, scriptPathPattern);
        }

    }

    protected void runStartupScript(@NotNull final ResourceResolver resolver, @NotNull final MODE mode,
                                    @NotNull final String scriptPath, @NotNull final Map<String, Object> variables,
                                    @NotNull final PrintWriter output, @NotNull final String statusPath, boolean force)
            throws PersistenceException {
        final Resource statusResource = provideResource(resolver, statusPath);
        if (statusResource != null) {
            if (force || shouldBeExecuted(resolver, mode, resolver.getResource(scriptPath), statusResource)) {
                LOG.debug("loading script '{}'...", scriptPath);
                try (final Reader scriptReader = openScript(resolver, getClass().getClassLoader(), scriptPath)) {
                    if (scriptReader != null) {
                        LOG.info("executing script '{}'...", scriptPath);
                        final ModifiableValueMap modifiableProps = statusResource.adaptTo(ModifiableValueMap.class);
                        if (modifiableProps != null) {
                            registerExceution(modifiableProps);
                            // commit to use it to 'lock' it if we are in a multi node environment
                            // (automatic mode only, not working if 'force' is 'on')
                            resolver.commit();
                            runGroovyScript(resolver, scriptReader, variables, output, null, scriptPath);
                            // commit() should be part of the script, if forgotten revert all(!) to ensure
                            // that a following execution doesn't commit the pending things maybe open here
                            resolver.revert();
                            LOG.info("execution finished '{}'.", scriptPath);
                        } else {
                            LOG.error("cannont modify status '{}'", statusPath);
                        }
                    } else {
                        LOG.error("cannot load script '{}'", scriptPath);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            } else {
                LOG.info("script '{}' not executed, no new version found", scriptPath);
            }
        } else {
            LOG.error("can't access status resource '{}'", statusPath);
        }
    }

    protected boolean shouldBeExecuted(@NotNull final ResourceResolver resolver, @NotNull final MODE mode,
                                       @Nullable final Resource scriptResource,
                                       @NotNull final Resource statusResource) {
        final ValueMap scriptProps = scriptResource != null ? scriptResource.getValueMap()
                : new ValueMapDecorator(Collections.emptyMap());
        final ValueMap statusProps = statusResource.getValueMap();
        final Calendar indicatorTime = mode == MODIFIED
                ? scriptProps.get(FILE_MODIFIED, Calendar.class)
                : getLastDeployed();
        final Calendar lastExecuted = statusProps.get(LAST_EXECUTED, Calendar.class);
        return indicatorTime != null && (lastExecuted == null || lastExecuted.before(indicatorTime));
    }

    protected void registerExceution(@NotNull final ModifiableValueMap statusProps) {
        final Calendar timestamp = Calendar.getInstance();
        statusProps.put(LAST_EXECUTED, timestamp);
        final List<Calendar> executions = new ArrayList<>(Arrays.asList(
                statusProps.get(EXECUTIONS, new Calendar[0])));
        executions.add(0, timestamp);
        statusProps.put(EXECUTIONS, executions.subList(0, Math.min(10, executions.size())).toArray());
    }

    @Override
    public void runGroovyScript(@NotNull final ResourceResolver resolver,
                                @NotNull final Reader scriptReader,
                                @NotNull final Map<String, Object> variables,
                                @NotNull final PrintWriter output,
                                @Nullable final Logger logger,
                                @NotNull final String name) {
        final ClassLoader classLoader = dynamicClassLoaderManager.getDynamicClassLoader();
        final Script script = getGroovyScript(resolver, classLoader, scriptReader, variables, output, logger, name);
        runSetupScript(resolver, classLoader, output, logger, script, variables);
        script.run();
    }

    @SuppressWarnings("unchecked")
    protected void runSetupScript(@NotNull final ResourceResolver resolver,
                                  @NotNull final ClassLoader classLoader,
                                  @NotNull final PrintWriter output,
                                  @Nullable final Logger logger,
                                  @NotNull final Script script,
                                  @NotNull final Map<String, Object> variables) {
        for (final String setupScriptPath : config.setupScripts()) {
            try (final Reader reader = openScript(resolver, classLoader, setupScriptPath)) {
                if (reader != null) {
                    final Script setupScript = getGroovyScript(resolver, classLoader, reader, variables, output, logger,
                            StringUtils.substringAfterLast(setupScriptPath, "/"));
                    setupScript.getBinding().setVariable("script", script);
                    Object result = setupScript.run();
                    if (result instanceof Map) {
                        ((Map<String, Object>) result).forEach((key, value) -> script.getBinding().setVariable(key, value));
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    protected @NotNull Script getGroovyScript(@NotNull final ResourceResolver resolver,
                                              @NotNull final ClassLoader classLoader,
                                              @NotNull final Reader scriptReader,
                                              @NotNull final Map<String, Object> variables,
                                              @NotNull final PrintWriter output,
                                              @Nullable final Logger logger,
                                              @NotNull final String name) {
        final CompilerConfiguration compilerConfig = getCompilerConfiguration();
        final Binding bindingObject = new Binding(getBinding(resolver, classLoader, variables, output, logger));
        final GroovyShell shell = new GroovyShell(classLoader, bindingObject, compilerConfig);
        return shell.parse(scriptReader, name);
    }

    protected @NotNull CompilerConfiguration getCompilerConfiguration() {
        final CompilerConfiguration compilerConfig = new CompilerConfiguration();
        compilerConfig.addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));
        compilerConfig.addCompilationCustomizers(new ASTTransformationCustomizer(
                Collections.singletonMap("value", config.threadTimeout()), TimedInterrupt.class));
        compilerConfig.addCompilationCustomizers(new ImportCustomizer().addStarImports(
                config.starImports()));
        return compilerConfig;
    }

    /**
     * prepare bindings compatible to the well known 'groovyconsole' if possible
     *
     * @param resolver    the resolver to use for execution
     * @param classLoader the classloader used to retrieve classess for objects to bind
     * @param variables   a set of additional, most recently added variables (which may override the default bindings)
     * @param output      the writer to bind for the script output
     * @param logger      the logger to bind
     * @return the map of bound objects
     */
    protected @NotNull Map<String, Object> getBinding(@NotNull final ResourceResolver resolver,
                                                      @NotNull final ClassLoader classLoader,
                                                      @NotNull final Map<String, Object> variables,
                                                      @NotNull final PrintWriter output,
                                                      @Nullable final Logger logger) {
        final Map<String, Object> bindings = new HashMap<>();
        final Session session = Objects.requireNonNull(resolver.adaptTo(Session.class));
        final Workspace workspace = Objects.requireNonNull(session.getWorkspace());
        QueryManager queryManager = null;
        try {
            queryManager = workspace.getQueryManager();
        } catch (RepositoryException ignore) {
        }
        bindings.put("log", logger != null ? logger : LOG);
        bindings.put("session", session);
        bindings.put("resourceResolver", resolver);
        bindings.put("resourceResolverFactory", resolverFactory);
        bindings.put("workspace", workspace);
        if (queryManager != null) {
            bindings.put("queryManager", queryManager);
        }
        bindings.put("bundleContext", bundleContext);
        bindings.put("out", output);
        bind(bindings, resolver, "pageManager", classLoader, "com.day.cq.wcm.api.PageManager");
        bind(bindings, resolver, "tagManager", classLoader, "com.day.cq.tagging.TagManager");
        bind(bindings, resolver, "queryBuilder", classLoader, "com.day.cq.search.QueryBuilder");
        bind(bindings, session, "nodeBuilder", classLoader,
                "com.icfolson" + _NODE_BUILDER,
                "be.orbinson" + _NODE_BUILDER);
        bind(bindings, session, "pageBuilder", classLoader,
                "com.icfolson" + _PAGE_BUILDER,
                "be.orbinson" + _PAGE_BUILDER);
        bindings.putAll(variables);
        return bindings;
    }

    protected void bind(@NotNull final Map<String, Object> bindings,
                        @NotNull final Object adaptable, @NotNull final String name,
                        @NotNull final ClassLoader classLoader, @NotNull final String... classNames) {
        for (final String className : classNames) {
            final Class<?> type = getClass(classLoader, className);
            if (type != null) {
                final Object value = adaptTo(adaptable, type);
                if (value != null) {
                    bindings.put(name, value);
                    break; // for
                }
            }
        }
    }

    protected <T> @Nullable T adaptTo(@Nullable final Object adaptable, @Nullable Class<T> type) {
        return type != null ? (adaptable instanceof Adaptable
                ? ((Adaptable) adaptable).adaptTo(type)
                : create(type, adaptable != null ? new Object[]{adaptable} : new Object[0])) : null;
    }

    protected <T> @Nullable T create(@Nullable Class<T> type, @NotNull final Object... arguments) {
        if (type != null) {
            try {
                final Class<?>[] argTypes = new Class[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    argTypes[i] = arguments[i].getClass();
                }
                return type.getConstructor(argTypes).newInstance(arguments);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                     InvocationTargetException ignore) {
            }
        }
        return null;
    }

    protected Class<?> getClass(@NotNull final ClassLoader classLoader, @NotNull final String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException ignore) {
        }
        return null;
    }

    @Override
    public @Nullable Reader openScript(@NotNull final ResourceResolver resolver,
                                       @NotNull final ClassLoader classLoader,
                                       @Nullable final String scriptPath) {
        Reader reader = null;
        if (StringUtils.isNotBlank(scriptPath)) {
            if (scriptPath.startsWith("resource:")) {
                final InputStream stream = classLoader.getResourceAsStream(scriptPath.substring(9));
                if (stream != null) {
                    reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                }
            } else {
                reader = openScript(resolver, resolver.getResource(scriptPath));
            }
        }
        return reader;
    }

    @Override
    public @Nullable Reader openScript(@NotNull final ResourceResolver resolver,
                                       @Nullable final Resource resource) {
        if (resource != null && resource.isResourceType(NT_FILE)) {
            final InputStream scriptStream = resource.getValueMap().get(FILE_DATA, InputStream.class);
            if (scriptStream != null) {
                return new InputStreamReader(scriptStream, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    protected @Nullable Resource provideResource(@NotNull final ResourceResolver resolver,
                                                 @NotNull final String path)
            throws PersistenceException {
        Resource resource = resolver.getResource(path);
        if (resource == null && !path.equals("/")) {
            final String name = StringUtils.substringAfterLast(path, "/");
            if (StringUtils.isNotBlank(name)) {
                final String parentPath = StringUtils.defaultIfEmpty(StringUtils.substringBeforeLast(path, "/"), "/");
                final Resource parent = provideResource(resolver, parentPath);
                if (parent != null) {
                    resource = resolver.create(parent, name,
                            Collections.singletonMap(JCR_PRIMARY_TYPE, NT_UNSTRUCTURED));
                }
            }
        }
        return resource;
    }

    protected class DropIt extends PrintWriter {

        public DropIt() {
            super(new Writer() {
                @Override
                public void write(char @NotNull [] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }
    }

    @SuppressWarnings("BusyWait")
    protected void waitForAllBundlesActive() {
        final long timeoutAt = System.currentTimeMillis() + (300 * 1000L);
        while (System.currentTimeMillis() < timeoutAt && !isAllBundlesActive()) {
            LOG.info("not all bundles active, waiting...");
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException ignore) {
            }
        }
    }

    protected boolean isAllBundlesActive() {
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getState() != ACTIVE) {
                return false;
            }
        }
        return true;
    }
}
