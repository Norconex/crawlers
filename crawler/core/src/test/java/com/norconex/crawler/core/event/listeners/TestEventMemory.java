package com.norconex.crawler.core.event.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.norconex.commons.lang.event.Event;

/**
 * Helper for sharing event memory between all TestMemEventTrackerListener
 * instances in the same test thread. Use {@code TestEventMemory.create()} in
 * a try-with-resources block, or {@code TestEventMemory.runWithMemory()} for
 * lambda scoping. Events are auto-cleared on close.
 *
 * @see TestEventMemoryListener
 */
public final class TestEventMemory implements AutoCloseable {
    private static final InheritableThreadLocal<List<Event>> EVENTS =
            new InheritableThreadLocal<>() {
                @Override
                protected List<Event> initialValue() {
                    return new ArrayList<>();
                }
            };

    private boolean closed = false;

    private TestEventMemory() {
        EVENTS.set(new ArrayList<>());
    }

    /**
     * Creates a new scoped event memory for the current thread.
     * Use in try-with-resources for auto-clear.
     * @return a new scoped instance for trackign events in memory
     */
    public static TestEventMemory create() {
        return new TestEventMemory();
    }

    /**
     * Runs the given code with a scoped event memory, auto-clearing after.
     * @param runnable runnable
     */
    public static void runWithMemory(Runnable runnable) {
        try (var mem = create()) {
            runnable.run();
        }
    }

    /**
     * Runs the given code with a scoped event memory, auto-clearing after.
     * Passes the TestEventMemory instance to the consumer.
     * @param consumer in-memory events consumer
     */
    public static void runWithMemory(Consumer<TestEventMemory> consumer) {
        try (var mem = create()) {
            consumer.accept(mem);
        }
    }

    /**
     * Adds an event to the current thread's memory.
     * @param event the event to add
     */
    public static void add(Event event) {
        EVENTS.get().add(event);
    }

    /**
     * Returns all captured events for the current thread.
     * @return the events that were tracked in memory
     */
    public static List<Event> getEvents() {
        return Collections.unmodifiableList(EVENTS.get());
    }

    /**
     * Clears the event memory for the current thread.
     * Called automatically on close.
     */
    @Override
    public void close() {
        if (!closed) {
            EVENTS.remove();
            closed = true;
        }
    }
}