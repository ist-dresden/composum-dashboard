package com.composum.sling.dashboard.service;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Composum Dashboard Extract Service configuration"
)
public @interface ResourceExtractConfig {

    @AttributeDefinition(
            name = "Predefined Paths",
            description = "the set of resource paths to extract in the 'predefined' mode"
    )
    String[] predefinedPaths();

    /**
     * @see com.composum.sling.dashboard.service.ResourceExtractService.PathMappingRule
     */
    @AttributeDefinition(
            name = "Path Rule Set",
            description = "source to target mapping rules; e.g. '/content/dam(/[^/]+(/.*)),dam:Asset,1,/content/dam/test$2'"
    )
    String[] pathRuleSet();

    /**
     * a set of additional entries to embed into an extracted ZIP for specific node types
     */
    @AttributeDefinition(
            name = "add. ZIP Entries",
            description = "entry patterns to add to ZIP: '{node:type},{relative/path/to/embed}'"
    )
    String[] addZipEntries() default {
            "dam:Asset,jcr:content/renditions/original"
    };

    @AttributeDefinition(
            name = "Ignored Children",
            description = "path patterns for resources children to ignore; e.g. '^.+/jcr:content/renditions/cq5dam\\..+$'"
    )
    String[] ignoredChildren();

    @AttributeDefinition(
            name = "Ignored Properties",
            description = "name patterns of properties to ignore; e.g. '^jcr:(created|lastModified).*$'"
    )
    String[] ignoredProperties() default {
            "^jcr:(uuid)$",
            "^jcr:(baseVersion|predecessors|versionHistory|isCheckedOut)$",
            "^jcr:(created|lastModified).*$",
            "^cq:last(Modified|Replicat|Rolledout).*$"
    };
}
