package com.composum.sling.dashboard.service;

import com.composum.sling.dashboard.service.TraceService.Level;
import com.composum.sling.dashboard.service.TraceService.TraceEntry;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component(
        service = {TraceManager.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true
)
@Designate(ocd = DefaultTraceManager.Config.class)
public class DefaultTraceManager implements TraceManager {

    protected final Map<String, TraceService> traceServices = new TreeMap<>();

    @Reference(
            service = TraceService.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            policyOption = ReferencePolicyOption.GREEDY
    )
    protected void bindTraceService(@NotNull final TraceService service) {
        synchronized (traceServices) {
            traceServices.put(service.getName(), service);
        }
    }

    protected void unbindTraceService(@NotNull final TraceService service) {
        synchronized (traceServices) {
            traceServices.remove(service.getName());
        }
    }

    @Override
    public @Nullable TraceService getTrace(@Nullable final String context) {
        return StringUtils.isNotBlank(context) ? traceServices.get(context) : traceServices.get("default");
    }

    @Override
    public int getTraceNumber() {
        return traceServices.size();
    }

    @Override
    public @NotNull Iterable<TraceService> getTraces() {
        List<TraceService> traces = new ArrayList<>(traceServices.values());
        traces.sort(new Comparator<>() {

            public static final String KEY_FMT = "%04d:%s";

            @Override
            public int compare(TraceService o1, TraceService o2) {
                return String.format(KEY_FMT, o1.getRank(), o1.getLabel())
                        .compareTo(String.format(KEY_FMT, o2.getRank(), o1.getLabel()));
            }
        });
        return traces;
    }

    @Override
    public void trace(@Nullable final String context,
                      @NotNull final Level level, @Nullable final String reference,
                      @NotNull final String message, final Object... args) {
        TraceService trace = getTrace(context);
        if (trace != null) {
            trace.trace(level, reference, message, args);
        }
    }

    @Override
    public @NotNull Iterable<TraceEntry> getEntries(@Nullable final String context, @Nullable Level level) {
        TraceService trace = getTrace(context);
        return trace != null ? trace.getEntries(level) : Collections.emptyList();
    }

    @ObjectClassDefinition(name = "Composum Dashboard Default Trace Manager",
            description = "Configuration to enable default trace services")
    public @interface Config {
    }

}
