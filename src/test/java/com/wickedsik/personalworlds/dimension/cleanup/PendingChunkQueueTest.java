package com.wickedsik.personalworlds.dimension.cleanup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PendingChunkQueue}: the chunk-load callback must only
 * queue work, and the tick-time drain must run it exactly once.
 */
class PendingChunkQueueTest {

    private PendingChunkQueue<String> queue;
    private List<String> handled;

    @BeforeEach
    void setUp() {
        queue = new PendingChunkQueue<>();
        handled = new ArrayList<>();
    }

    private void record(String world, long pos) {
        handled.add(world + ":" + pos);
    }

    @Test
    @DisplayName("Enqueue does not run any work")
    void enqueue_doesNotHandle() {
        queue.enqueue("w", 1L);

        assertTrue(handled.isEmpty());
        assertFalse(queue.isEmpty());
    }

    @Test
    @DisplayName("Drain handles each queued position once and empties the queue")
    void drain_handlesAllOnce() {
        queue.enqueue("a", 1L);
        queue.enqueue("b", 2L);
        queue.enqueue("a", 3L);

        queue.drain(this::record);

        assertEquals(List.of("a:1", "a:3", "b:2"), handled);
        assertTrue(queue.isEmpty());

        queue.drain(this::record);
        assertEquals(3, handled.size());
    }

    @Test
    @DisplayName("Duplicate positions in one batch collapse to one")
    void duplicates_collapse() {
        queue.enqueue("w", 7L);
        queue.enqueue("w", 7L);

        queue.drain(this::record);

        assertEquals(List.of("w:7"), handled);
    }

    @Test
    @DisplayName("Work enqueued during a drain runs in the next drain, not the current one")
    void enqueueDuringDrain_deferredToNextBatch() {
        queue.enqueue("w", 1L);

        queue.drain((world, pos) -> {
            record(world, pos);
            queue.enqueue(world, pos + 1);
        });

        assertEquals(List.of("w:1"), handled);
        assertFalse(queue.isEmpty());

        queue.drain(this::record);
        assertEquals(List.of("w:1", "w:2"), handled);
    }

    @Test
    @DisplayName("Clear drops queued work")
    void clear_dropsWork() {
        queue.enqueue("w", 1L);
        queue.clear();

        queue.drain(this::record);

        assertTrue(handled.isEmpty());
        assertTrue(queue.isEmpty());
    }
}
