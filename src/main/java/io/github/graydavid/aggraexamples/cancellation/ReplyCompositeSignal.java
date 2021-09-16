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
import io.github.graydavid.aggra.core.Dependencies.SameMemoryDependency;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.GraphCall;
import io.github.graydavid.aggra.core.GraphValidators;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Reply;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.core.Type;

public class ReplyCompositeSignal {
    private ReplyCompositeSignal() {}

    // Every Graph needs a Memory subclass
    private static class ExampleMemory extends Memory<Void> {
        private ExampleMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static final Node<ExampleMemory, Void> ENTRY_NODE;
    private static final GraphCall.NoInputFactory<ExampleMemory> GRAPH_CALL_FACTORY;
    static {
        // Create the nodes in the graph
        Node<ExampleMemory, Integer> loopFor100 = nodeLoopingForNumLoops(100);
        Node<ExampleMemory, Integer> loopFor1000000 = nodeLoopingForNumLoops(1000000);
        Node.CommunalBuilder<ExampleMemory> entryNodeBuilder = Node.communalBuilder(ExampleMemory.class);
        SameMemoryDependency<ExampleMemory, Integer> consume100 = entryNodeBuilder
                .sameMemoryUnprimedDependency(loopFor100);
        SameMemoryDependency<ExampleMemory, Integer> consume1000000 = entryNodeBuilder
                .sameMemoryUnprimedDependency(loopFor1000000);
        ENTRY_NODE = entryNodeBuilder.type(Type.generic("ChooseAndCancel"))
                .role(Role.of("ChooseBetween100And1000000"))
                .build(device -> {
                    Reply<Integer> reply100 = device.call(consume100);
                    Reply<Integer> reply1000000 = device.call(consume1000000);
                    return Reply.anyOfBacking(reply100, reply1000000).thenApply(ignore -> {
                        device.ignore(reply100);
                        device.ignore(reply1000000);

                        System.out.println("loopFor100 looped for " + reply100.join());
                        System.out.println("loopFor1000000 looped for " + reply1000000.join());
                        return null;
                    });
                });

        // Create the Graph and a convenient GraphCall factory
        Graph<ExampleMemory> graph = Graph.fromRoots(Role.of("ReplyCompositeSignal"), Set.of(ENTRY_NODE));
        GRAPH_CALL_FACTORY = GraphCall.NoInputFactory.from(graph, ExampleMemory::new);
    }

    private static Node<ExampleMemory, Integer> nodeLoopingForNumLoops(int numLoops) {
        return Node.communalBuilder(ExampleMemory.class)
                .type(Type.generic("NumLooping"))
                .role(Role.of("WaitForLoops" + numLoops))
                .graphValidatorFactory(GraphValidators.ignoringWillTriggerReplyCancelSignal())
                .buildWithCompositeCancelSignal((device, signal) -> {
                    return CompletableFuture.supplyAsync(() -> {
                        int i = 0;
                        for (; i < numLoops && !signal.read(); ++i) {
                            // Do nothing busy work
                            int j = 0;
                            j = j + 1;
                        }
                        return i;
                    });
                });
    }

    public static void main(String args[]) {
        Observer observer = Observer.doNothing(); // We don't want to observe any node calls
        GraphCall<ExampleMemory> graphCall = GRAPH_CALL_FACTORY.openCancellableCall(observer);

        CompletableFuture<Reply<Void>> doneOrAbandonedReply = graphCall.finalCallAndWeaklyCloseOrAbandonOnTimeout(
                ENTRY_NODE, 5, TimeUnit.SECONDS, ReplyCompositeSignal::handleCallState);

        doneOrAbandonedReply.join().join();
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
