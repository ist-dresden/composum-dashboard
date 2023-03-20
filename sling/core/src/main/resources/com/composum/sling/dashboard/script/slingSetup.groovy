package com.composum.sling.dashboard.script

import org.apache.sling.api.resource.ResourceResolver

import javax.jcr.query.Query

/**
 * the default setup script to prepare the groovy script 'script'
 * - can add some meta methods for the script and
 * - cn return the additional/default binding variables for the script
 * executed by the runner with the additional variables
 * - script: the groovy Script object
 */

script.metaClass.save << { ->
    session.save()
}

script.metaClass.commit << { ->
    resourceResolver.commit()
}

script.metaClass.getNode << { String path ->
    session.getNode(path)
}

script.metaClass.getResource << { String path ->
    resourceResolver.getResource(path)
}

script.metaClass.move = { String src ->
    ["to": { String dst ->
        session.move(src, dst)
        session.save()
    }]
}

script.metaClass.rename = { Node node ->
    ["to": { String newName ->
        def parent = node.parent

        delegate.move node.path to parent.path + "/" + newName

        if (parent.primaryNodeType.hasOrderableChildNodes()) {
            def nextSibling = node.nextSibling as Node

            if (nextSibling) {
                parent.orderBefore(newName, nextSibling.name)
            }
        }

        session.save()
    }]
}

script.metaClass.copy = { String src ->
    ["to": { String dst ->
        session.workspace.copy(src, dst)
    }]
}

// simple XPath query (similar to groovyconsole)

Query.metaClass.setHitsPerPage << { value -> delegate.limit = value }

script.metaClass.buildQueryString << { Map predicatesTemplate ->
    def predicates = new LinkedHashMap<>(predicatesTemplate)
    StringBuilder query = new StringBuilder("/jcr:root")
    if (predicates['path']) {
        def path = predicates['path']
        query.append(path)
        if (!path.endsWith('/')) {
            query.append('/')
        }
    } else {
        query.append('/')
    }
    query.append('/')
    if (predicates['type']) {
        query.append('element(')
    }
    if (predicates['name']) {
        query.append(predicates['name'])
    } else {
        query.append('*')
    }
    if (predicates['type']) {
        query.append(',').append(predicates['type']).append(')')
    }
    predicates.remove('path')
    predicates.remove('name')
    predicates.remove('type')
    int size = predicates.size()
    if (size > 0) {
        query.append('[')
        predicates.eachWithIndex { entry, index ->
            if (entry.value) {
                if (entry.value.indexOf('%') >= 0) {
                    query.append('jcr:like(')
                    query.append(entry.key)
                    query.append(",'")
                    query.append(entry.value)
                    query.append("')")
                } else {
                    if ('.' == entry.key) {
                        query.append("jcr:contains(.,'")
                        query.append(entry.value)
                        query.append("')")
                    } else {
                        query.append('@')
                        query.append(entry.key)
                        query.append("='")
                        query.append(entry.value)
                        query.append("'")
                    }
                }
            } else {
                query.append('@')
                query.append(entry.key)
            }
            if (index < size-1) {
                query.append(' and ')
            }
        }
        query.append(']')
    }
    query.toString()
}

script.metaClass.createQuery << { Map predicates ->
    String query = script.buildQueryString(predicates)
    return queryManager.createQuery(query, Query.XPATH)
}

ResourceResolver.metaClass.findResources << { Map predicates ->
    String query = script.buildQueryString(predicates)
    return delegate.findResources(query, Query.XPATH)
}

// services

script.metaClass.getModel = { String path, Class type ->
    def modelFactoryReference = bundleContext.getServiceReference("org.apache.sling.models.factory.ModelFactory")
    def modelFactory = bundleContext.getService(modelFactoryReference)
    def resource = resourceResolver.resolve(path)
    modelFactory.createModel(resource, type)
}

script.metaClass.getService << { Class serviceClass ->
    def serviceRef = bundleContext.getServiceReference(serviceClass)
    serviceRef ? bundleContext.getService(serviceRef) : null
}

script.metaClass.getService << { String serviceClass ->
    def serviceRef = bundleContext.getServiceReference(serviceClass)
    serviceRef ? bundleContext.getService(serviceRef) : null
}

script.metaClass.getServices << { Class serviceClass, String filter ->
    def serviceRefs = bundleContext.getServiceReferences(serviceClass, filter)
    serviceRefs.collect { bundleContext.getService(it) }
}

script.metaClass.getServices << { String serviceClass, String filter ->
    def serviceRefs = bundleContext.getServiceReferences(serviceClass, filter)
    serviceRefs.collect { bundleContext.getService(it) }
}

// the result, a map of custom bindings

[:]