package dev.labwatch.collect;

import dev.labwatch.model.Source;
import dev.labwatch.store.StatusStore;
import dev.labwatch.visibility.CollectedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polls every registered collector on a fixed interval and writes the
 *  merged result into the {@link StatusStore}. A failing collector keeps
 *  its last-known services so a dead upstream does not blank the page. */
public class PollLoop {

    private static final Logger LOG = LoggerFactory.getLogger(PollLoop.class);

    private final StatusStore store;
    private final List<CollectorEntry> entries;

    private PollLoop(StatusStore store, List<CollectorEntry> entries) {
        this.store = store;
        this.entries = entries;
    }

    /** Create the poll loop and begin scheduling. */
    public static PollLoop start(StatusStore store,
                                  Map<String, Collector> collectors,
                                  Duration interval) {
        List<CollectorEntry> entries = new ArrayList<>();
        for (var e : collectors.entrySet()) {
            entries.add(new CollectorEntry(e.getKey(), e.getValue()));
        }
        PollLoop loop = new PollLoop(store, entries);
        LOG.info("poll loop started: {} collector(s) every {}s",
                entries.size(), interval.toSeconds());
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "labwatch-poll");
                    t.setDaemon(true);
                    return t;
                });
        scheduler.scheduleWithFixedDelay(loop::tick, 0,
                interval.toMillis(), TimeUnit.MILLISECONDS);
        return loop;
    }

    /** Package-private for PollLoopTest — creates entries but does not
     *  start a scheduler, so tests can drive ticks manually. */
    static PollLoop forTesting(StatusStore store,
                                Map<String, Collector> collectors) {
        List<CollectorEntry> entries = new ArrayList<>();
        for (var e : collectors.entrySet()) {
            entries.add(new CollectorEntry(e.getKey(), e.getValue()));
        }
        return new PollLoop(store, entries);
    }

    /** Package-private so PollLoopTest can drive ticks manually. */
    void tick() {
        List<Source> sources = new ArrayList<>();
        List<CollectedService> allServices = new ArrayList<>();
        for (var entry : entries) {
            boolean ok = false;
            try {
                List<CollectedService> services = entry.collector.collect();
                entry.lastServices = List.copyOf(services);
                entry.lastSuccess = Instant.now();
                entry.lastError = null;
                ok = true;
            } catch (Exception e) {
                LOG.error("{} collector failed", entry.name, e);
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                if (entry.lastSuccess == null) {
                    entry.lastError = "first poll has not yet succeeded: " + msg;
                } else {
                    entry.lastError = msg;
                }
            }
            sources.add(new Source(entry.name,
                    ok,
                    entry.lastSuccess,
                    entry.lastError));
            allServices.addAll(entry.lastServices);
        }
        store.update(sources, allServices);
    }

    static class CollectorEntry {
        final String name;
        final Collector collector;
        List<CollectedService> lastServices = List.of();
        Instant lastSuccess;
        String lastError;

        CollectorEntry(String name, Collector collector) {
            this.name = name;
            this.collector = collector;
        }
    }
}
