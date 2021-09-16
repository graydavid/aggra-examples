/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.cancellation;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.graydavid.aggra.core.CallObservers.Observer;
import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.GraphCall;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Reply;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.FunctionNodes;

public class GraphCallStandard {
    private GraphCallStandard() {}

    // Every Graph needs a Memory subclass
    private static class ExampleMemory extends Memory<Void> {
        private ExampleMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static final Node<ExampleMemory, String> NODE_1;
    private static final Node<ExampleMemory, String> NODE_2;
    private static final GraphCall.NoInputFactory<ExampleMemory> GRAPH_CALL_FACTORY;
    static {
        // Create the nodes in the graph
        NODE_1 = FunctionNodes.synchronous(Role.of("BeNode1"), ExampleMemory.class).getValue("node-1");
        NODE_2 = FunctionNodes.synchronous(Role.of("BeNode2"), ExampleMemory.class).getValue("node-2");

        // Create the Graph and a convenient GraphCall factory
        Graph<ExampleMemory> graph = Graph.fromRoots(Role.of("GraphCallStandardGraph"), Set.of(NODE_1, NODE_2));
        GRAPH_CALL_FACTORY = GraphCall.NoInputFactory.from(graph, ExampleMemory::new);
    }

    public static void main(String args[]) {
        Observer observer = Observer.doNothing(); // We don't want to observe any node calls
        GraphCall<ExampleMemory> graphCall = GRAPH_CALL_FACTORY.openCancellableCall(observer);

        Reply<String> reply1 = graphCall.call(NODE_1);
        graphCall.triggerCancelSignal();
        Reply<String> reply2 = graphCall.call(NODE_2);

        CompletableFuture<GraphCall.State> doneOrAbandoned = graphCall.weaklyCloseOrAbandonOnTimeout(5,
                TimeUnit.SECONDS);
        handleState(doneOrAbandoned.join());

        System.out.println("Reply 1: " + reply1);
        System.out.println("Reply 2: " + reply2);
        System.out.println("Reply 2 Exception: " + reply2.getFirstNonContainerExceptionNow());
    }

    private static void handleState(GraphCall.State state) {
        if (state.isAbandoned()) {
            System.out.println(
                    "Error: we had to abandon graph call. Some processes may still be running in the background");
        }

        state.getUnhandledExceptions().stream().forEach(t -> t.printStackTrace());
    }
}
