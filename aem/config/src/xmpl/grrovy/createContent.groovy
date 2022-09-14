dashboardManager = bundleContext.getService(bundleContext.getServiceReference(
        'com.composum.sling.dashboard.service.DashboardManager'))
dashboardManager.createContentPage(slingRequest, slingResponse, '/content/test/insights',
        bundleContext.getService(bundleContext.getServiceReferences(
                'com.composum.sling.dashboard.service.ContentGenerator', '(name=dashboard)')[0]), '{\
  "navigation": {\
    "browser": {\
      "jcr:title": "Browser",\
      "linkPath": "/content/test/insights/browser"\
    }\
  }\
}')
dashboardManager.createContentPage(slingRequest, slingResponse, '/content/test/insights/browser',
        bundleContext.getService(bundleContext.getServiceReferences(
                'com.composum.sling.dashboard.service.ContentGenerator', '(name=browser)')[0]))
resourceResolver.commit()