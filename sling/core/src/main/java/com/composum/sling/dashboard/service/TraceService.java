package com.composum.sling.dashboard.service;

import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;

public interface TraceService {

    enum Level {
        ERROR, WARNING, SUCCESS, INFO, DEBUG
    }

    interface TraceEntry {

        @NotNull Date getTime();

        @NotNull Level getLevel();

        @Nullable String getReference();

        @NotNull String getMessage();

        @Nullable <T> T getProperty(@NotNull String name, T defaultValue);

        void toJson(@NotNull JsonWriter writer) throws IOException;
    }

    @NotNull String getName();

    @NotNull String getLabel();

    int getRank();

    /**
     * Adds a trace entry to the services trace buffer.
     *
     * @param level     the trace entry level
     * @param reference a repository path or a similar hint for the related content
     * @param message   a test message prepared via String.format() using the given arguments
     * @param args      a set of message arguments and an appended optional Map object containg trace entry properties
     */
    void trace(@NotNull Level level, @Nullable String reference, @NotNull String message, Object... args);

    /**
     * Returns the buffered entries of the given level or higher.
     *
     * @param level the level of interest, if 'null' all entries will be returned
     * @return the entries for iteration
     */
    @NotNull Iterable<TraceEntry> getEntries(@Nullable Level level);

    int getNumber(@NotNull Level level);
}
