/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.CaptureResponseNodes;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.onemoretry.Try;

public class CaptureResponse {
    private CaptureResponse() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, String> returnOrThrow = FunctionNodes.synchronous(Role.of("ReturnOrThrow"), TestMemory.class)
                .get(() -> {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        return "Result";
                    }
                    throw new IllegalStateException();
                });
        Node<TestMemory, Try<String>> captureReturnOrThrow = CaptureResponseNodes
                .startNode(Role.of("CaptureReturnOrThrow"), TestMemory.class)
                .captureResponse(returnOrThrow);
        System.out.println(captureReturnOrThrow);
    }
}
