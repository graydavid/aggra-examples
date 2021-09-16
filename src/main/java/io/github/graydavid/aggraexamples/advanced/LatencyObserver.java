/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.graydavid.aggra.core.CallObservers.ObservationType;
import io.github.graydavid.aggra.core.CallObservers.Observer;
import io.github.graydavid.aggra.core.CallObservers.ObserverBeforeStart;
import io.github.graydavid.aggra.core.Caller;
import io.github.graydavid.aggra.core.Node;

public class LatencyObserver {
    private LatencyObserver() {}

    public static void main(String args[]) {
        ConcurrentLinkedQueue<LatencyRecord> latencyRecords = new ConcurrentLinkedQueue<>();
        ObserverBeforeStart<Object> latencyRecordingObserver = latencyRecordingObserver(latencyRecords);
        Observer observer = Observer.builder()
                .observerBeforeFirstCall(latencyRecordingObserver)
                .observerBeforeBehavior(latencyRecordingObserver)
                .build();
        System.out.println(observer);

        // Perform a graph call here and wait for it to be done

        // Process the latency records here
    }

    private static ObserverBeforeStart<Object> latencyRecordingObserver(
            ConcurrentLinkedQueue<LatencyRecord> latencyRecords) {
        return (type, caller, node, memory) -> {
            long start = System.nanoTime();
            return (result, throwable) -> {
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                LatencyRecord record = new LatencyRecord(type, caller, node, duration);
                latencyRecords.add(record);
            };
        };
    }

    public static class LatencyRecord {
        private final ObservationType type;
        private final Caller caller;
        private final Node<?, ?> node;
        private final Duration duration;

        public LatencyRecord(ObservationType type, Caller caller, Node<?, ?> node, Duration duration) {
            this.type = type;
            this.caller = caller;
            this.node = node;
            this.duration = duration;
        }

        public ObservationType getType() {
            return type;
        }

        public Caller getCaller() {
            return caller;
        }

        public Node<?, ?> getNode() {
            return node;
        }

        public Duration getDuration() {
            return duration;
        }
    }
}
