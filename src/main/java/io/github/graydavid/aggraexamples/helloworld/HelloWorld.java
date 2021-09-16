/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.helloworld;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

public class HelloWorld {
    private HelloWorld() {}

    // Every Graph needs a Memory subclass
    private static class HelloWorldMemory extends Memory<String> {
        private HelloWorldMemory(MemoryScope scope, CompletionStage<String> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static final Node<HelloWorldMemory, String> GET_HELLO_WORLD;
    private static final GraphCall.Factory<String, HelloWorldMemory> GRAPH_CALL_FACTORY;
    static {
        // Create the nodes in the graph
        Node<HelloWorldMemory, String> getHello = Node.inputBuilder(HelloWorldMemory.class)
                .role(Role.of("GetHello"))
                .build();
        Node<HelloWorldMemory, String> getWorld = FunctionNodes.synchronous(Role.of("GetWorld"), HelloWorldMemory.class)
                .get(() -> "World");
        GET_HELLO_WORLD = FunctionNodes.synchronous(Role.of("GetHelloWorld"), HelloWorldMemory.class)
                .apply((hello, world) -> hello + " " + world, getHello, getWorld);

        // Create the Graph and a convenient GraphCall factory
        Graph<HelloWorldMemory> graph = Graph.fromRoots(Role.of("HelloWorldGraph"), Set.of(GET_HELLO_WORLD));
        GRAPH_CALL_FACTORY = GraphCall.Factory.from(graph, HelloWorldMemory::new);
    }

    public static void main(String args[]) {
        Observer observer = Observer.doNothing(); // We don't want to observe any node calls
        GraphCall<HelloWorldMemory> graphCall = GRAPH_CALL_FACTORY.openCancellableCall("Hello", observer);
        CompletableFuture<Reply<String>> helloWorldDoneOrAbandoned = graphCall
                // Best practice is to set a timeout if calling join on unknown Future
                .finalCallAndWeaklyCloseOrAbandonOnTimeout(GET_HELLO_WORLD, 5, TimeUnit.SECONDS,
                        HelloWorld::handleCallState);
        // Either the following throws (if abandoned and reply not done) or is fine (and helloWorld is fine)
        Reply<String> helloWorld = helloWorldDoneOrAbandoned.join();
        System.out.println(helloWorld.join());
    }

    private static void handleCallState(GraphCall.State state, Throwable throwable, Reply<String> finalReply) {
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
