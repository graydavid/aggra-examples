/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.ConditionNodes;
import io.github.graydavid.aggra.nodes.FunctionNodes;

public class Condition {
    private Condition() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, Boolean> checkServiceCallRequired = FunctionNodes
                .synchronous(Role.of("CheckServiceCallRequired"), TestMemory.class)
                .get(() -> ThreadLocalRandom.current().nextBoolean());
        Node<TestMemory, String> makeServiceCall = FunctionNodes
                .synchronous(Role.of("MakeServiceCall"), TestMemory.class)
                .getValue("ServiceResponse");
        Node<TestMemory, Optional<String>> optionallyMakeServiceCall = ConditionNodes
                .startNode(Role.of("OptionallyMakeServiceCall"), TestMemory.class)
                .ifThen(checkServiceCallRequired, makeServiceCall);
        System.out.println(optionallyMakeServiceCall);
    }
}
