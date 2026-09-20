package org.binclass.algorithms.eventbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.binclass.algorithms.eventbus.EventBus.EventBusListener;
import org.binclass.algorithms.events.AbstractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventBusTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private static EventBusImpl eventBus = new EventBusImpl();

    private WeakHashMap<EventBusListener, Object> listenersBackup;
    private static CountDownLatch latch = new CountDownLatch(1);
    
    @BeforeEach
    void setStreams() {
        listenersBackup = eventBus.eventListeners;
        eventBus.eventListeners = new WeakHashMap<>();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    void restoreInitialStreams() {
        var log = out.toString();
        System.setOut(originalOut);
        System.setErr(originalErr);

        System.out.println(log);
        eventBus.eventListeners = listenersBackup;
    }

    @Test
    @SuppressWarnings({ "unused", "java:S1854", "java:S2925" })
    void eventFiredAndRemoval() {
        var listener1 = new TestListener();
        var listener2 = new TestListener();
        var listener3 = new TestListener();
        var event = new MessageEvent("Hello", LocalDateTime.now());
        eventBus.post(event);
        // Wait for latch
        try {
            latch.await();
            wait10ms(); // Wait loggers to print
        } catch (InterruptedException _) {
            // Ignore
        }

        assertEquals(1, listener1.getEventCount());
        assertEquals("Hello", listener1.getLastEvent().message());
        assertEquals(1, listener2.getEventCount());
        assertEquals("Hello", listener2.getLastEvent().message());
        assertEquals(1, listener3.getEventCount());
        assertEquals("Hello", listener3.getLastEvent().message());

        listener1.remove();
        listener3 = null;

        System.gc();
        wait100ms(); // Wait for GC to run

        event = new MessageEvent("World", LocalDateTime.now());
        latch = new CountDownLatch(1);

        eventBus.post(event);
        // Wait for latch
        try {
            latch.await();
            wait10ms(); // Wait loggers to print
        } catch (InterruptedException _) {
            // Ignore
        }

        assertEquals(1, listener1.getEventCount());
        assertEquals(2, listener2.getEventCount());
        assertEquals("World", listener2.getLastEvent().message());
        assertTrue(out.toString().contains("event fired for 1 recipients."));

        listener2.remove();
    }

    private void wait100ms() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException _) {
            // Ignore
        }
    }

    private void wait10ms() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException _) {
            // Ignore
        }
    }

    static class TestListener implements EventBusListener {

        private AtomicInteger count = new AtomicInteger(0);
        private MessageEvent event;

        public TestListener() {
            eventBus.registerEventBusListener(this);
        }

        @Override
        public void eventFired(AbstractEvent event) {
            count.incrementAndGet();
            if (event instanceof MessageEvent) {
                this.event = (MessageEvent) event;
            }
            latch.countDown();
        }

        public int getEventCount() {
            return count.get();
        }

        public MessageEvent getLastEvent() {
            return event;
        }

        public void remove() {
            eventBus.unregisterEventBusListener(this);
        }
    }

    static record MessageEvent(String message, LocalDateTime timestamp)
            implements AbstractEvent {
    }
}
