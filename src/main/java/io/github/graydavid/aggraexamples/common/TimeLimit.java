/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

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
import io.github.graydavid.aggra.nodes.TimeLimitNodes;

public class TimeLimit {
    private TimeLimit() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        ExecutorService executor = Executors.newCachedThreadPool();
        Node<TestMemory, String> neverFinish = CompletionFunctionNodes
                .threadLingering(Role.of("NeverFinish"), TestMemory.class)
                .getValue(new CompletableFuture<>());
        Node<TestMemory, String> timeboundCallNeverFinish = TimeLimitNodes
                .startNode(Role.of("TimeboundCallNeverFinish"), TestMemory.class)
                .executor(executor)
                .timeout(1, TimeUnit.MILLISECONDS)
                .timeLimitedCall(neverFinish);
        System.out.println(timeboundCallNeverFinish);
    }
}
