/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.cancellation;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.graydavid.aggra.core.Behaviors.Behavior;
import io.github.graydavid.aggra.core.CallObservers.Observer;
import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Dependencies.NewMemoryDependency;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.GraphCall;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Reply;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.core.Type;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.MemoryTripNodes;
import io.github.graydavid.aggra.nodes.TimeLimitNodes;

public class MemoryScopeStandard {
    private MemoryScopeStandard() {}

    // Every Graph needs a Memory subclass
    private static class ParentMemory extends Memory<Void> {
        private ParentMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    private static class ChildMemory extends Memory<Void> {
        private ChildMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    private static class GrandchildMemory extends Memory<Void> {
        private GrandchildMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static final Node<ParentMemory, Void> PARENT_NODE;
    private static final GraphCall.NoInputFactory<ParentMemory> GRAPH_CALL_FACTORY;
    static {
        // Create the nodes in the graph
        Node<GrandchildMemory, Void> doNothing = FunctionNodes.synchronous(Role.of("DoNothing"), GrandchildMemory.class)
                .run(() -> {
                });
        Node.CommunalBuilder<ChildMemory> callDoNothingBuilder = Node.communalBuilder(ChildMemory.class);
        NewMemoryDependency<GrandchildMemory, Void> consumeDoNothing = callDoNothingBuilder
                .newMemoryDependency(doNothing);
        Node<ChildMemory, Void> callDoNothingUntilFailure = callDoNothingBuilder.type(Type.generic("DoNothingCalling"))
                .role(Role.of("CallDoNothingUntilFailure"))
                .build(callUntilFailure(consumeDoNothing));
        Node<ChildMemory, Void> timeLimitedDoNothingUntilFailure = TimeLimitNodes
                .startNode(Role.of("TimeLimitedDoNothingUntilFailure"), ChildMemory.class)
                .callerThreadExecutor()
                .timeout(1, TimeUnit.MILLISECONDS)
                .timeLimitedCall(callDoNothingUntilFailure);
        PARENT_NODE = MemoryTripNodes.startNode(Role.of("CreateChildAndStartDoNothingUntilFailure"), ParentMemory.class)
                .createMemoryNoInputAndCall((scope, parent) -> new ChildMemory(scope),
                        timeLimitedDoNothingUntilFailure);

        // Create the Graph and a convenient GraphCall factory
        Graph<ParentMemory> graph = Graph.fromRoots(Role.of("MemoryScopeStandardGraph"), Set.of(PARENT_NODE));
        GRAPH_CALL_FACTORY = GraphCall.NoInputFactory.from(graph, ParentMemory::new);
    }

    private static Behavior<ChildMemory, Void> callUntilFailure(NewMemoryDependency<GrandchildMemory, Void> node) {
        return device -> CompletableFuture.runAsync(() -> {
            int numCalls = 0;
            Reply<Void> doNothingReply = null;
            do {
                doNothingReply = device.createMemoryNoInputAndCall((scope, parent) -> new GrandchildMemory(scope),
                        node);
                numCalls++;
            } while (!doNothingReply.isCompletedExceptionally());
            System.out.println("Able to make this many calls to doNothing: " + numCalls);
            System.out.println("DoNothing Exception: " + doNothingReply.getFirstNonContainerExceptionNow());
        });
    }

    public static void main(String args[]) {
        Observer observer = Observer.doNothing(); // We don't want to observe any node calls
        GraphCall<ParentMemory> graphCall = GRAPH_CALL_FACTORY.openCancellableCall(observer);

        CompletableFuture<Reply<Void>> parentReply = graphCall.finalCallAndWeaklyCloseOrAbandonOnTimeout(PARENT_NODE, 5,
                TimeUnit.SECONDS, MemoryScopeStandard::handleCallState);

        parentReply.join();
        System.out.println("Program done");
    }

    private static void handleCallState(GraphCall.State state, Throwable throwable, Reply<Void> finalReply) {
        if (state.isAbandoned()) {
            System.out.println(
                    "Error: we had to abandon graph call. Some processes may still be running in the background");
        }

        if (throwable == null) {
            state.getUnhandledExceptions().stream().forEach(t -> t.printStackTrace());
        } else {
            throwable.printStackTrace();
        }
    }
}
