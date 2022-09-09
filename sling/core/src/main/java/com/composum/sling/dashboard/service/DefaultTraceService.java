package com.composum.sling.dashboard.service;

import com.composum.sling.dashboard.util.Properties;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component(
        service = {TraceService.class}, immediate = true
)
@Designate(ocd = DefaultTraceService.Config.class, factory = true)
public class DefaultTraceService implements TraceService {

    @ObjectClassDefinition(name = "Default Trace Service")
    @interface Config {

        @AttributeDefinition(name = "Name")
        String name() default "default";

        @AttributeDefinition(name = "Label")
        String label();

        @AttributeDefinition(name = "Trace Level")
        String traceLevel() default "info";

        @AttributeDefinition(name = "Trace Max")
        int traceMax() default 50;

        @AttributeDefinition(name = "Keep Level")
        String keepLevel() default "error";

        @AttributeDefinition(name = "Keep Max")
        int keepMax() default 50;

        @AttributeDefinition(name = "Time Format")
        String timeFormat() default "yyyy-MM-dd HH:mm:ss.SSSZ";

        @AttributeDefinition(name = "Rank")
        int rank() default 1000;
    }

    protected class DefaultTraceEntry implements TraceEntry {

        private final Date time;
        private final Level level;
        private final String reference;
        private final String message;
        private final Object[] args;

        private transient ValueMap properties;

        protected DefaultTraceEntry(@NotNull final Level level, @Nullable final String reference,
                                    @NotNull final String message, @Nullable final Object[] args) {
            this.time = new Date(System.currentTimeMillis());
            this.level = level;
            this.reference = reference;
            this.message = message;
            this.args = args != null ? args : new Object[0];
        }

        @Override
        public @NotNull Date getTime() {
            return time;
        }

        @Override
        public @NotNull Level getLevel() {
            return level;
        }

        @Override
        public @Nullable String getReference() {
            return reference;
        }

        @Override
        public @NotNull String getMessage() {
            try {
                return args.length > 1 || (args.length == 1 && !(args[0] instanceof Map))
                        ? String.format(message, args) : message;
            } catch (RuntimeException ex) {
                return ex + " (" + message + ")[" + Arrays.toString(args) + "]";
            }
        }

        @Override
        public @Nullable <T> T getProperty(@NotNull String name, T defaultValue) {
            return getProperties().get(name, defaultValue);
        }

        @SuppressWarnings("unchecked")
        protected @NotNull ValueMap getProperties() {
            if (properties == null) {
                Map<String, Object> map = args.length > 0 && args[args.length - 1] instanceof Map ?
                        (Map<String, Object>) args[args.length - 1] : Collections.emptyMap();
                properties = map instanceof ValueMap ? (ValueMap) map : new ValueMapDecorator(map);
            }
            return properties;
        }

        @Override
        public void toJson(@NotNull JsonWriter writer) throws IOException {
            writer.beginObject();
            writer.name("time").value(new SimpleDateFormat(timeFormat).format(getTime()));
            writer.name("level").value(getLevel().name().toLowerCase());
            writer.name("message").value(getMessage());
            final String reference = getReference();
            if (reference != null) {
                writer.name("reference").value(reference);
            }
            final Map<?, ?> properties = getProperties();
            if (!properties.isEmpty()) {
                writer.name("properties");
                Properties.toJson(writer, properties, timeFormat);
            }
            writer.endObject();
        }
    }

    protected String name;
    protected String label;
    protected Level traceLevel;
    protected int traceMax;
    protected Level keepLevel;
    protected int keepMax;
    protected String timeFormat;
    protected int rank;

    protected int keepLevelCount;
    protected final List<TraceEntry> entries = new ArrayList<>();

    @Activate
    @Modified
    protected void activate(Config config) {
        name = config.name();
        label = config.label();
        entries.clear();
        try {
            traceLevel = Level.valueOf(config.traceLevel().toUpperCase());
        } catch (IllegalArgumentException ignore) {
            traceLevel = Level.INFO;
        }
        traceMax = Math.min(Math.max(0, config.traceMax()), 1000);
        try {
            keepLevel = Level.valueOf(config.keepLevel().toUpperCase());
        } catch (IllegalArgumentException ignore) {
            keepLevel = Level.ERROR;
        }
        keepMax = Math.min(Math.max(0, config.keepMax()), 1000);
        keepLevelCount = 0;
        timeFormat = config.timeFormat();
        rank = config.rank();
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getLabel() {
        return StringUtils.isNotBlank(label) ? label : getName();
    }

    @Override
    public int getRank() {
        return rank;
    }

    @Override
    public void trace(@NotNull final Level level, @Nullable final String reference,
                      @NotNull final String message, final Object... args) {
        if (traceLevel.compareTo(level) >= 0) {
            synchronized (entries) {
                int max = traceMax + (keepLevel.compareTo(level) >= 0 ? keepMax : keepLevelCount);
                while (entries.size() >= max) {
                    int index = 0;
                    if (keepLevelCount < keepMax || keepLevel.compareTo(level) < 0) {
                        while (index <= keepLevelCount && keepLevel.compareTo(entries.get(index).getLevel()) >= 0) {
                            index++;
                        }
                    }
                    TraceEntry entry = entries.remove(index);
                    if (keepLevel.compareTo(entry.getLevel()) >= 0) {
                        keepLevelCount--;
                    }
                }
                entries.add(new DefaultTraceEntry(level, reference, message, args));
                if (keepLevel.compareTo(level) >= 0) {
                    keepLevelCount++;
                }
            }
        }
    }

    @Override
    public @NotNull Iterable<TraceEntry> getEntries(@Nullable Level level) {
        List<TraceEntry> result = new ArrayList<>();
        for (TraceEntry entry : entries) {
            if (level == null || entry.getLevel().compareTo(level) >= 0) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public int getNumber(@NotNull Level level) {
        int count = 0;
        for (TraceEntry entry : entries) {
            if (entry.getLevel() == level) {
                count++;
            }
        }
        return count;
    }
}
