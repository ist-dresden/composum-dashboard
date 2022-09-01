package com.composum.sling.dashboard.service;

import com.composum.sling.dashboard.service.TraceService.Level;
import com.composum.sling.dashboard.service.TraceService.TraceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TraceManager {

    /**
     * Adds a trace entry to the services trace buffer.
     *
     * @param context   the key of the preferred trace service to use, if 'null' the default context is used
     * @param level     the trace entry level
     * @param reference a repository path or a similar hint for the related content
     * @param message   a test message prepared via String.format() using the given arguments
     * @param args      a set of message arguments and an appended optional Map object containg trace entry properties
     */
    void trace(@Nullable String context, @NotNull Level level, @Nullable String reference,
               @NotNull String message, Object... args);

    /**
     * Returns the buffered entries of the given level or higher.
     *
     * @param context the key of the preferred trace service to use, if 'null' the default context is used
     * @param level   the level of interest, if 'null' all entries will be returned
     * @return the entries for iteration
     */
    @NotNull Iterable<TraceEntry> getEntries(@Nullable String context, @Nullable Level level);

    int getTraceNumber();

    @NotNull Iterable<TraceService> getTraces();

    @Nullable TraceService getTrace(@Nullable String context);
}
