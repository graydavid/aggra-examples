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
import io.github.graydavid.aggra.nodes.FunctionNodes;

public class Function {
    private Function() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, String> getA = FunctionNodes.synchronous(Role.of("GetA"), TestMemory.class).getValue("A");
        Node<TestMemory, String> getB = FunctionNodes.synchronous(Role.of("GetB"), TestMemory.class).getValue("B");
        Node<TestMemory, String> getC = FunctionNodes.synchronous(Role.of("GetC"), TestMemory.class).getValue("C");
        Node<TestMemory, String> getAbc = FunctionNodes.synchronous(Role.of("GetAbc"), TestMemory.class)
                .apply((a, b, c) -> a + b + c, getA, getB, getC);
        System.out.println(getAbc);
    }
}
