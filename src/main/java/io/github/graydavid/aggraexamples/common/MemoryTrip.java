/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

import java.util.Set;
import java.util.concurrent.CompletionStage;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.MemoryTripNodes;

public class MemoryTrip {
    private MemoryTrip() {}

    private static class MainMemory extends Memory<Integer> {
        private MainMemory(MemoryScope scope, CompletionStage<Integer> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    private static class SecondaryMemory extends Memory<Integer> {
        private final MainMemory mainMemory;

        private SecondaryMemory(MemoryScope scope, CompletionStage<Integer> input, MainMemory mainMemory) {
            super(scope, input, Set.of(mainMemory), () -> new ConcurrentHashMapStorage());
            this.mainMemory = mainMemory;
        }

        public MainMemory getMainMemory() {
            return mainMemory;
        }
    }

    public static void main(String args[]) {
        Node<MainMemory, Integer> getFactor = FunctionNodes.synchronous(Role.of("GetFactor"), MainMemory.class)
                .getValue(10);
        Node<SecondaryMemory, Integer> getFactorFromMain = MemoryTripNodes
                .startNode(Role.of("GetFactorFromMain"), SecondaryMemory.class)
                .accessAncestorMemoryAndCall(SecondaryMemory::getMainMemory, getFactor);
        Node<SecondaryMemory, Integer> getSecondaryInput = Node.inputBuilder(SecondaryMemory.class)
                .role(Role.of("GetSecondaryInput"))
                .build();
        Node<SecondaryMemory, Integer> multiplySecondaryInputByFactor = FunctionNodes
                .synchronous(Role.of("MultiplySecondaryInputByFactor"), SecondaryMemory.class)
                .apply((num, factor) -> num * factor, getSecondaryInput, getFactorFromMain);
        Node<MainMemory, Integer> getMainInput = Node.inputBuilder(MainMemory.class)
                .role(Role.of("GetMainInput"))
                .build();
        Node<MainMemory, Integer> multiplyMainInputByFactor = MemoryTripNodes
                .startNode(Role.of("MultiplyMainInputByFactor"), MainMemory.class)
                .createMemoryAndCall(SecondaryMemory::new, getMainInput, multiplySecondaryInputByFactor);
        System.out.println(multiplyMainInputByFactor);
    }
}
