package dev.labwatch.collect;

import dev.labwatch.visibility.CollectedService;

import java.io.IOException;
import java.util.List;

/** Every upstream source implements this. The M04 poll scheduler calls
 *  {@code collect()} on a timer; failures bubble as IOException so the
 *  scheduler can mark a source degraded without crashing the loop. */
public interface Collector {

    List<CollectedService> collect() throws IOException;
}
