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

public class Input {
    private Input() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope, CompletableFuture<Integer> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, Integer> getInput = Node.inputBuilder(TestMemory.class).role(Role.of("GetInput")).build();
        System.out.println(getInput);
    }
}
