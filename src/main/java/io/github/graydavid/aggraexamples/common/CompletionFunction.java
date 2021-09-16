/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.CompletionFunctionNodes;

public class CompletionFunction {
    private CompletionFunction() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, String> getA = CompletionFunctionNodes.threadLingering(Role.of("GetA"), TestMemory.class)
                .getValue(CompletableFuture.completedFuture("A"));
        Node<TestMemory, String> getB = CompletionFunctionNodes.threadLingering(Role.of("GetB"), TestMemory.class)
                .getValue(CompletableFuture.completedFuture("B"));
        Node<TestMemory, String> getC = CompletionFunctionNodes.threadLingering(Role.of("GetC"), TestMemory.class)
                .getValue(CompletableFuture.completedFuture("C"));
        Node<TestMemory, String> getAbc = CompletionFunctionNodes.threadLingering(Role.of("GetAbc"), TestMemory.class)
                .apply((a, b, c) -> CompletableFuture.completedFuture(a + b + c), getA, getB, getC);
        System.out.println(getAbc);
    }
}
