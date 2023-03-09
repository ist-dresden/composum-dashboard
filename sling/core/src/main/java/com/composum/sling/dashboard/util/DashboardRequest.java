package com.composum.sling.dashboard.util;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * the request wrapper supports the use of a service user and provides a corresponding resource resolver
 * if such a service resolver can be created (if a usable service user is avaiable)
 */
public class DashboardRequest extends SlingHttpServletRequestWrapper implements AutoCloseable {

    public class WrappedResource extends ResourceWrapper {

        public WrappedResource(@NotNull Resource resource) {
            super(resource);
        }

        @Override
        public @NotNull ResourceResolver getResourceResolver() {
            return serviceResolver;
        }
    }

    private ResourceResolver serviceResolver;
    private Resource wrappedResource;

    private final Map<Class<?>, Object> services = new HashMap<>();
    private transient BundleContext bundleContext;

    public DashboardRequest(SlingHttpServletRequest wrappedRequest) {
        super(wrappedRequest);
        wrappedRequest.getSession(true); // ensure that a session is initialized
        try {
            serviceResolver = getService(ResourceResolverFactory.class).getServiceResourceResolver(null);
            wrappedResource = new WrappedResource(wrappedRequest.getResource());
        } catch (Exception ex) {
            wrappedResource = wrappedRequest.getResource();
        }
    }

    @Override
    public void close() {
        if (serviceResolver != null) {
            serviceResolver.close();
            serviceResolver = null;
        }
    }

    @Override
    public @NotNull ResourceResolver getResourceResolver() {
        return serviceResolver != null ? serviceResolver : super.getResourceResolver();
    }

    @SuppressWarnings("unchecked")
    protected <T> T getService(Class<T> type) {
        T service = (T) services.get(type);
        if (service == null) {
            BundleContext context = getBundleContext();
            service = context.getService(context.getServiceReference(type));
            if (service != null) {
                services.put(type, service);
            }
        }
        return service;
    }

    protected BundleContext getBundleContext() {
        if (bundleContext == null) {
            bundleContext = FrameworkUtil.getBundle(DashboardRequest.class).getBundleContext();
        }
        return bundleContext;
    }
}
