package com.composum.sling.dashboard.script

println "\nbinding: " + binding
binding.variables.each({ entry ->
    println "  " + entry.key + " = " + entry.value
});

println "\nmeta: methods..."
getMetaClass().methods.each({ method ->
    println "  " + method.name + " (" + method + ")"
})

println "\nlog: " + log
println "\nservice test: " + getService(ResourceResolverFactory.class)
println ""