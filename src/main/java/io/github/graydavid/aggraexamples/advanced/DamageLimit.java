/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.MemoryTripNodes;
import io.github.graydavid.aggra.nodes.TimeLimitNodes;

public class DamageLimit {
    private DamageLimit() {}

    private static class MainMemory extends Memory<Integer> {
        private MainMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    private static class DependencyMemory extends Memory<Integer> {
        private DependencyMemory(MemoryScope scope, CompletionStage<Integer> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<DependencyMemory, Integer> getDependencyInput = Node.inputBuilder(DependencyMemory.class)
                .role(Role.of("GetDependencyInput"))
                .build();
        Node<DependencyMemory, Integer> runDependency = FunctionNodes
                .synchronous(Role.of("RunDependency"), DependencyMemory.class)
                .apply(num -> num + 100, getDependencyInput);
        Node<DependencyMemory, Integer> callDependencyWithTimeout = TimeLimitNodes
                .startNode(Role.of("CallDependencyWithTimeout"), DependencyMemory.class)
                .callerThreadExecutor()
                .timeout(10, TimeUnit.MILLISECONDS)
                .timeLimitedCall(runDependency);
        Node<MainMemory, Integer> callDependencyInIsolation = MemoryTripNodes
                .startNode(Role.of("CallDependencyInIsolation"), MainMemory.class)
                .createMemoryNoInputAndCall(
                        (scope, parent) -> new DependencyMemory(scope, CompletableFuture.completedFuture(4)),
                        callDependencyWithTimeout);
        System.out.println(callDependencyInIsolation);
    }
}
