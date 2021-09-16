/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.CompletionFunctionNodes;

public class AbandonTask {
    private AbandonTask() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Node<TestMemory, Void> abandonTask = CompletionFunctionNodes
                .threadLingering(Role.of("AbandonTask"), TestMemory.class)
                .get(() -> {
                    return CompletableFuture.runAsync(AbandonTask::doTask, executor).orTimeout(1, TimeUnit.SECONDS);
                });
        System.out.println(abandonTask);
    }

    private static void doTask() {
        // Something important here
    }
}
