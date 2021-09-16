/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.github.graydavid.aggra.core.ByTypeVisitor;
import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.core.Type;
import io.github.graydavid.aggra.nodes.CaptureResponseNodes;
import io.github.graydavid.aggra.nodes.CompletionFunctionNodes;
import io.github.graydavid.aggra.nodes.ConditionNodes;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.IterationNodes;
import io.github.graydavid.aggra.nodes.MemoryTripNodes;
import io.github.graydavid.aggra.nodes.TimeLimitNodes;
import io.github.graydavid.aggra.nodes.TryWithResourceNodes;

public class TypeVisitation {
    private TypeVisitation() {}

    private static class TestMemory extends Memory<Integer> {
        private TestMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, Integer> unknownTypeNode = Node.communalBuilder(TestMemory.class)
                .type(Type.generic("unknown"))
                .role(Role.of("BeUnknown"))
                .build(device -> CompletableFuture.completedFuture(13));
        Node<TestMemory, Integer> functionTypeNode = FunctionNodes
                .synchronous(Role.of("BeFunctional"), TestMemory.class)
                .getValue(4);
        Graph<TestMemory> graph = Graph.fromRoots(Role.of("BeGraph"), Set.of(unknownTypeNode, functionTypeNode));
        ByTypeVisitor<String> shapeCalculator = yedShapeCalculator();
        graph.getAllNodes()
                .stream()
                .map(node -> node.getRole() + ": " + shapeCalculator.visit(node))
                .forEach(string -> System.out.println(string));
    }

    private static ByTypeVisitor<String> yedShapeCalculator() {
        Map<Type, Function<Node<?, ?>, String>> typeToFunction = new HashMap<>();
        typeToFunction.put(CaptureResponseNodes.CAPTURE_RESPONSE_TYPE, node -> "trapezoid");
        typeToFunction.put(CompletionFunctionNodes.JUMPING_COMPLETION_FUNCTION_TYPE, node -> "ellipse");
        typeToFunction.put(CompletionFunctionNodes.LINGERING_COMPLETION_FUNCTION_TYPE, node -> "ellipse");
        typeToFunction.put(ConditionNodes.CONDITION_TYPE, node -> "diamond");
        typeToFunction.put(IterationNodes.ITERATION_TYPE, node -> "octagon");
        typeToFunction.put(FunctionNodes.SYNCHRONOUS_FUNCTION_TYPE, node -> "ellipse");
        typeToFunction.put(FunctionNodes.ASYNCHRONOUS_FUNCTION_TYPE, node -> "ellipse");
        typeToFunction.put(MemoryTripNodes.ANCESTOR_ACCESSOR_MEMORY_TRIP_TYPE, node -> "up-arrow");
        typeToFunction.put(MemoryTripNodes.CREATE_MEMORY_TRIP_TYPE, node -> "down-arrow");
        typeToFunction.put(TimeLimitNodes.TIME_LIMIT_TYPE, node -> "fat-right-arrow");
        typeToFunction.put(TryWithResourceNodes.TRY_WITH_RESOURCE_TYPE, node -> "rectangle");
        typeToFunction.put(Node.InputBuilder.INPUT_TYPE, node -> "right-skewed-parallelogram");
        Function<Node<?, ?>, String> defaultVisitor = node -> "8-pointed-star";
        return ByTypeVisitor.ofVisitors(typeToFunction, defaultVisitor);
    }
}
