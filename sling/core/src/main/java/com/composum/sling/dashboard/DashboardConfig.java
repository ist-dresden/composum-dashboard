package com.composum.sling.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public interface DashboardConfig {

    String JCR_CONTENT = "jcr:content";
    String JCR_TITLE = "jcr:title";
    String JCR_DATA = "jcr:data";
    String JCR_PRIMARY_TYPE = "jcr:primaryType";
    String JCR_MIXIN_TYPES = "jcr:mixinTypes";
    String JCR_LAST_MODIFIED = "jcr:lastModified";
    String JCR_CREATED = "jcr:created";
    String JCR_MIME_TYPE = "jcr:mimeType";
    String SLING_RESOURCE_TYPE = "sling:resourceType";
    String NT_UNSTRUCTURED = "nt:unstructured";
    String NT_RESOURCE = "nt:resource";
    String NT_FILE = "nt:file";
    String SLING_FOLDER = "sling:Folder";

    String JSON_DATE_FORMAT = "yyyy-MM-dd MM:mm:ss.SSSZ";
    String XML_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    static String getFirstProperty(@Nullable final String[] stringSet, final String defaultValue) {
        return stringSet != null && stringSet.length > 0 ? stringSet[0] : defaultValue;
    }

    static List<Pattern> patternList(@Nullable final String[] config) {
        List<Pattern> patterns = new ArrayList<>();
        for (String rule : config) {
            if (StringUtils.isNotBlank(rule)) {
                patterns.add(Pattern.compile(rule));
            }
        }
        return patterns;
    }
}
