/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryBridges.MemoryFactory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.IterationNodes;

public class Iteration {
    private Iteration() {}

    private static class MainMemory extends Memory<Integer> {
        private MainMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    private static class SecondaryMemory extends Memory<Integer> {
        private SecondaryMemory(MemoryScope scope, CompletionStage<Integer> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<SecondaryMemory, Integer> getInput = Node.inputBuilder(SecondaryMemory.class)
                .role(Role.of("GetInput"))
                .build();
        Node<SecondaryMemory, Integer> multiplyInputByTwo = FunctionNodes
                .synchronous(Role.of("MultipleInputByTwo"), SecondaryMemory.class)
                .apply(num -> 2 * num, getInput);
        Node<MainMemory, List<Integer>> getList = FunctionNodes.synchronous(Role.of("GetList"), MainMemory.class)
                .getValue(List.of(5, 9, 10, 30));
        // javac (but not Eclipse) has a problem inferring type arguments when the memory factory is declared inline,
        // so break it out into a separate variable definition.
        MemoryFactory<MainMemory, Integer, SecondaryMemory> secondaryMemoryFactory = (scope, input,
                main) -> new SecondaryMemory(scope, input);
        Node<MainMemory, List<Integer>> multiplyListByTwo = IterationNodes
                .startNode(Role.of("MultiplyListByTwo"), MainMemory.class)
                .iterate(getList, secondaryMemoryFactory, multiplyInputByTwo)
                .collectToOutputList();
        System.out.println(multiplyListByTwo);
    }
}
