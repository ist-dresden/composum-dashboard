package com.composum.sling.dashboard.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.jetbrains.annotations.NotNull;

import javax.jcr.query.Query;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JcrQuery {

    public static final String FULL_TEXT = "text";

    public static final Pattern SQL2_QUERY = Pattern.compile("^(union +)?select +.*$", Pattern.CASE_INSENSITIVE);
    public static final Pattern XPATH_QUERY = Pattern.compile("^(/jcr:root)?/[^ ]*([*(\\[].*)?$");
    public static final Pattern TEXT_QUERY = Pattern.compile(
            "^((\\[(?<type>[^]]+)])?(?<path>/[^/*(\\[ ]*(/[^/*(\\[ ]*)*)? +)?(?<text>.*)$");

    public static final Pattern SPLIT_TERMS = Pattern.compile(" +");

    public static final Pattern NAME_AND_TEXT = Pattern.compile("^\\[(?<name>[^]]+)](?<text>.+)$");

    private final String pattern;
    private final String queryType;

    protected Matcher textMatcher;
    protected String textQuery;

    @SuppressWarnings("deprecation")
    public JcrQuery(@NotNull final String pattern) {
        if (SQL2_QUERY.matcher(pattern).matches()) {
            queryType = Query.JCR_SQL2;
        } else if (!XPATH_QUERY.matcher(pattern).matches()
                && (textMatcher = TEXT_QUERY.matcher(pattern)).matches()) {
            queryType = FULL_TEXT;
        } else {
            queryType = Query.XPATH;
        }
        if (Query.XPATH.equals(queryType)) {
            this.pattern = pattern.startsWith("/jcr:root/") ? pattern : ("/jcr:root" + pattern);
        } else {
            this.pattern = pattern;
        }
    }

    public @NotNull Iterator<Resource> find(@NotNull final ResourceResolver resolver) {
        if (FULL_TEXT.equals(queryType)) {
            return resolver.findResources(getTextQuery(), Query.JCR_SQL2);
        } else {
            return resolver.findResources(pattern, queryType);
        }
    }

    public @NotNull String getQuery() {
        if (FULL_TEXT.equals(queryType)) {
            return getTextQuery();
        } else {
            return pattern;
        }
    }

    public @NotNull String getQueryType() {
        return queryType;
    }

    public @NotNull String getTextQuery() {
        if (textQuery == null) {
            textQuery = textQuery();
        }
        return textQuery;
    }

    protected @NotNull String textQuery() {
        String type = StringUtils.defaultIfBlank(textMatcher.group("type"), "nt:base");
        String path = StringUtils.defaultIfBlank(textMatcher.group("path"), "/content");
        StringBuilder query = new StringBuilder();
        query.append("select * from [").append(type).append("] as node where")
                .append(" isdescendantnode(node, '").append(path).append("') and (");
        Matcher propMatcher;
        String property = "*";
        String operator = "";
        boolean openCond = true;
        String chr;
        StringBuilder collector = new StringBuilder();
        for (String term : SPLIT_TERMS.splitAsStream(textMatcher.group("text"))
                .filter(StringUtils::isNotBlank).collect(Collectors.toList())) {
            if (collector.length() == 0) {
                if (term.startsWith("+")) {
                    term = term.substring(1);
                    if (!openCond) {
                        query.append(" or ");
                        openCond = true;
                    }
                } else {
                    if (!openCond) {
                        query.append(" and ");
                        openCond = true;
                    }
                }
                if (term.startsWith("-")) {
                    term = term.substring(1);
                    query.append("not ");
                }
            }
            if (collector.length() > 0) {
                collector.append(" ");
                if (term.endsWith("\"")) {
                    collector.append(term, 0, term.length() - 1);
                    if (StringUtils.isBlank(operator)) {
                        collector.append("*");
                    }
                    term = collector.toString();
                    collector = new StringBuilder();
                } else {
                    collector.append(term);
                    continue;
                }
            } else {
                if ((propMatcher = NAME_AND_TEXT.matcher(term)).matches()) {
                    property = propMatcher.group("name");
                    term = propMatcher.group("text");
                }
                while (StringUtils.isNotBlank(term) && "=><".contains(chr = term.substring(0, 1))) {
                    if (!operator.contains(chr)) {
                        operator += chr;
                    }
                    term = term.substring(1);
                }
                if (term.startsWith("\"")) {
                    if (StringUtils.isBlank(operator)) {
                        collector.append("*");
                    }
                    term = term.substring(1);
                    if (term.endsWith("\"")) {
                        collector.append(term, 0, term.length() - 1);
                        if (StringUtils.isBlank(operator)) {
                            collector.append("*");
                        }
                        term = collector.toString();
                        collector = new StringBuilder();
                    } else {
                        collector.append(term);
                        continue;
                    }
                }
            }
            if (StringUtils.isNotBlank(term)) {
                if (StringUtils.isNotBlank(operator)) {
                    if (operator.contains(">")) { // ensure that a possible '><' will be '<>'
                        operator = operator.replaceAll(">", "") + ">";
                    }
                    if (operator.contains("=")) { // ensure that a possible '=>' will be '>='
                        operator = operator.replaceAll("=", "") + "=";
                    }
                    query.append("[").append(property).append("]")
                            .append(operator).append("'").append(term).append("'");
                } else {
                    if (term.contains("*")) {
                        query.append("[").append(property).append("] like '")
                                .append(term.replace('*', '%')).append("'");
                    } else {
                        query.append("contains([").append(property)
                                .append("],'").append(term).append("')");
                    }
                }
                property = "*";
                operator = "";
                openCond = false;
            }
        }
        query.append(")");
        return query.toString();
    }
}
