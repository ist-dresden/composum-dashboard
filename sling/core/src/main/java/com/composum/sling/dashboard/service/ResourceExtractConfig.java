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

    @AttributeDefinition(
            name = "Path Rule Set",
            description = "source to target mapping rules; e.g. '/content/dam(/[^/]+(/.*)),dam:Asset,1,/content/dam/test$2'"
    )
    String[] pathRuleSet();

    @AttributeDefinition(
            name = "add. ZIP Entries",
            description = "entry patterns to add to ZIP; e.g. 'dam:Asset,jcr:content/renditions/original'"
    )
    String[] addZipEntries();

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
