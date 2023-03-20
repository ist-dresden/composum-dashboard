package com.composum.sling.dashboard.script

import com.day.cq.replication.ReplicationOptions

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

// AEM...

script.metaClass.getPage = { String path ->
    pageManager.getPage(path)
}

script.metaClass.activate = { String path, ReplicationOptions options = null ->
    replicator.replicate(session, ReplicationActionType.ACTIVATE, path, options)
}

script.metaClass.deactivate = { String path, ReplicationOptions options = null ->
    replicator.replicate(session, ReplicationActionType.DEACTIVATE, path, options)
}

script.metaClass.createQuery { Map predicates ->
    queryBuilder.createQuery(PredicateGroup.create(predicates), session)
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