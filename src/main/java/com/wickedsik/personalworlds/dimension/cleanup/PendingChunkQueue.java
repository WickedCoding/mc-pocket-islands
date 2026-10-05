package com.wickedsik.personalworlds.dimension.cleanup;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Chunk positions waiting to be sanitized, grouped per world. Positions are
 * packed {@code ChunkPos} longs; duplicates within one batch collapse.
 *
 * The chunk-load callback only enqueues. The work runs later from
 * {@link #drain}, which the server-tick handler calls once all chunk-loading
 * task pumps have unwound. Entries enqueued while a drain is running land in
 * the next batch, so a drain never processes its own follow-up work.
 *
 * Not thread-safe: both sides run on the server thread.
 *
 * @param <W> the world handle type (a {@code ServerLevel} in production)
 */
final class PendingChunkQueue<W> {

    @FunctionalInterface
    interface Handler<W> {
        void handle(W world, long chunkPos);
    }

    private Map<W, Set<Long>> pending = new LinkedHashMap<>();

    void enqueue(W world, long chunkPos) {
        pending.computeIfAbsent(world, w -> new LinkedHashSet<>()).add(chunkPos);
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    void clear() {
        pending.clear();
    }

    /**
     * Hands every queued position to the handler and empties the queue. The
     * batch is swapped out before iterating, so the handler may enqueue
     * safely.
     */
    void drain(Handler<W> handler) {
        if (pending.isEmpty()) {
            return;
        }
        Map<W, Set<Long>> batch = pending;
        pending = new LinkedHashMap<>();
        for (Map.Entry<W, Set<Long>> entry : batch.entrySet()) {
            for (long chunkPos : entry.getValue()) {
                handler.handle(entry.getKey(), chunkPos);
            }
        }
    }
}
