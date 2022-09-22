package com.composum.sling.dashboard.util;

import com.composum.sling.dashboard.service.ResourceFilter;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.xss.XSSAPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import static com.composum.sling.dashboard.servlet.AbstractDashboardServlet.JCR_CONTENT;

public class Properties {

    // HTML (table)

    public static String toHtml(@NotNull final PrintWriter writer, @NotNull final Resource resource,
                                @NotNull final String name, @Nullable final Object value,
                                @NotNull final ResourceFilter filter, @NotNull final XSSAPI xssapi) {
        String type = "";
        if (value != null) {
            if (value instanceof Object[]) {
                writer.append("<ul>");
                for (Object val : (Object[]) value) {
                    writer.append("<li>");
                    type = toHtml(writer, resource, name, val, filter, xssapi);
                    writer.append("</li>");
                }
                writer.append("</ul>");
                type += "[]";
            } else if (value instanceof Iterable) {
                writer.append("<ul>");
                for (Object val : (Iterable<?>) value) {
                    writer.append("<li>");
                    type = toHtml(writer, resource, name, val, filter, xssapi);
                    writer.append("</li>");
                }
                writer.append("</ul>");
                type += "[]";
            } else if (value instanceof Calendar) {
                writer.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").format(((Calendar) value).getTime()));
                type = "Date";
            } else if (value instanceof InputStream) {
                writer.append("<a class=\"binary\" data-target=\"_blank\" data-href=\"")
                        .append(StringUtils.substringBeforeLast(resource.getPath(), "/" + JCR_CONTENT))
                        .append("\">download...</a>");
                type = "Binary";
            } else if (value instanceof String) {
                final String string = (String) value;
                Resource target = resolvePath(resource.getResourceResolver(), string, filter);
                if (target == null) {
                    target = resolveType(resource.getResourceResolver(), string, filter);
                }
                if (target != null) {
                    writer.append("<a class=\"path\" href=\"#\" data-path=\"").append(target.getPath()).append("\">")
                            .append(xssapi.encodeForHTML(string)).append("</a>");
                } else {
                    writer.append(xssapi.encodeForHTML(string));
                }
                type = "String";
            } else {
                writer.append(xssapi.encodeForHTML(value.toString()));
                type = value.getClass().getSimpleName();
            }
        }
        return type;
    }

    protected static @Nullable Resource resolveType(@NotNull final ResourceResolver resolver,
                                                    @Nullable final String type,
                                                    @NotNull final ResourceFilter filter) {
        if (StringUtils.isNotBlank(type) && StringUtils.countMatches(type, "/") > 1) {
            Resource resource;
            for (String root : resolver.getSearchPath()) {
                resource = resolvePath(resolver, root + type, filter);
                if (resource != null) {
                    return resource;
                }
            }
        }
        return null;
    }

    protected static @Nullable Resource resolvePath(@NotNull final ResourceResolver resolver,
                                                    @Nullable final String path,
                                                    @NotNull final ResourceFilter filter) {
        if (StringUtils.isNotBlank(path) && path.startsWith("/")) {
            final Resource resource = resolver.getResource(path);
            if (resource != null && filter.isAllowedResource(resource)) {
                return resource;
            }
        }
        return null;
    }

    // JSON

    public static void toJson(@NotNull final JsonWriter writer, @Nullable final Object value,
                              @NotNull final String dateFormat)
            throws IOException {
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof Object[]) {
            writer.beginArray();
            for (Object item : (Object[]) value) {
                toJson(writer, item, dateFormat);
            }
            writer.endArray();
        } else if (value instanceof Collection) {
            writer.beginArray();
            for (Object item : (Collection<?>) value) {
                toJson(writer, item, dateFormat);
            }
            writer.endArray();
        } else if (value instanceof Map) {
            writer.beginObject();
            for (Map.Entry<?, ?> item : ((Map<?, ?>) value).entrySet()) {
                writer.name(item.getKey().toString());
                toJson(writer, item.getValue(), dateFormat);
            }
            writer.endObject();
        } else if (value instanceof Calendar) {
            writer.value(new SimpleDateFormat(dateFormat).format(((Calendar) value).getTime()));
        } else if (value instanceof Date) {
            writer.value(new SimpleDateFormat(dateFormat).format((Date) value));
        } else if (value instanceof Boolean) {
            writer.value((Boolean) value);
        } else if (value instanceof Long) {
            writer.value((Long) value);
        } else if (value instanceof Integer) {
            writer.value((Integer) value);
        } else if (value instanceof Double) {
            writer.value((Double) value);
        } else if (value instanceof Number) {
            writer.value((Number) value);
        } else {
            writer.value(value.toString());
        }
    }

    // XML

    public static void toXml(@NotNull final PrintWriter writer,
                             @Nullable final Object value, @NotNull final String dateFormat) {
        if (value != null) {
            if (value instanceof Object[]) {
                toXmlArray(writer, (Object[]) value, dateFormat);
            } else if (value instanceof Collection) {
                toXmlArray(writer, ((Collection<?>) value).toArray(), dateFormat);
            } else {
                writer.append(xmlString(value, dateFormat));
            }
        }
    }

    public static void toXmlArray(@NotNull final PrintWriter writer,
                                  @NotNull final Object[] values, @NotNull final String dateFormat) {
        writer.append("[");
        for (int i = 0; i < values.length; ) {
            final String string = xmlString(values[i], dateFormat);
            writer.append(string.replace(",", "\\,"));
            if (++i < values.length) {
                writer.append(",");
            }
        }
        writer.append("]");
    }

    public static @NotNull String xmlType(@Nullable final Object value) {
        if (value instanceof Object[]) {
            Object[] values = (Object[]) value;
            return xmlType(values.length > 0 ? values[0] : null);
        } else if (value instanceof Collection) {
            Collection<?> values = (Collection<?>) value;
            return xmlType(values.isEmpty() ? null : values.iterator().next());
        } else if (value instanceof Calendar || value instanceof Date) {
            return "{Date}";
        } else if (value instanceof Boolean) {
            return "{Boolean}";
        } else if (value instanceof Long || value instanceof Integer) {
            return "{Long}";
        } else if (value instanceof Double || value instanceof Float) {
            return "{Double}";
        } else if (value instanceof BigDecimal) {
            return "{Decimal}";
        }
        return "";
    }

    public static @NotNull String xmlString(@Nullable final Object value, @NotNull final String dateFormat) {
        if (value instanceof Calendar) {
            return xmlString(new SimpleDateFormat(dateFormat).format(((Calendar) value).getTime()));
        } else if (value instanceof Date) {
            return xmlString(new SimpleDateFormat(dateFormat).format((Date) value));
        } else {
            return value != null ? xmlString(value.toString()) : "";
        }
    }

    public static @NotNull String xmlString(@NotNull final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("\"", "&quot;")
                .replace("\t", "&#x9;")
                .replace("\n", "&#xa;")
                .replace("\r", "&#xd;")
                .replace("\\", "\\\\");
    }

    private Properties() {
    }
}
