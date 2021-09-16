/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.cancellation;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.graydavid.aggra.core.Behaviors.BehaviorWithCustomCancelAction;
import io.github.graydavid.aggra.core.Behaviors.CompositeCancelSignal;
import io.github.graydavid.aggra.core.Behaviors.CustomCancelAction;
import io.github.graydavid.aggra.core.Behaviors.CustomCancelActionBehaviorResponse;
import io.github.graydavid.aggra.core.CallObservers.Observer;
import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Dependencies.SameMemoryDependency;
import io.github.graydavid.aggra.core.DependencyCallingDevices.DependencyCallingDevice;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.GraphCall;
import io.github.graydavid.aggra.core.GraphValidators;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Reply;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.core.Type;

public class NonInterruptCustomAction {
    private NonInterruptCustomAction() {}

    // Every Graph needs a Memory subclass
    private static class ExampleMemory extends Memory<Void> {
        private ExampleMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    /**
     * According to "Java Concurrency in Practice", java.net.Socket blocks during reading (or at least its InputStream
     * does). Interrupts won't stop that. Closing will. Simulate this with a simple Socket-like class backed by a
     * CountDownLatch.
     */
    private static class Socket {
        private final CountDownLatch stopReading = new CountDownLatch(1);

        public void read() {
            try {
                stopReading.await();
            } catch (InterruptedException e) {
                Thread.interrupted();
                throw new RuntimeException(e);
            }
        }

        public void close() {
            stopReading.countDown();
        }
    }

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static final Node<ExampleMemory, Void> ENTRY_NODE;
    private static final GraphCall.NoInputFactory<ExampleMemory> GRAPH_CALL_FACTORY;
    static {
        // Create the nodes in the graph
        Node<ExampleMemory, Void> readFromSocket = Node.communalBuilder(ExampleMemory.class)
                .type(Type.generic("SocketReading"))
                .role(Role.of("ReadFromSocket"))
                .graphValidatorFactory(GraphValidators.ignoringWillTriggerReplyCancelSignal())
                .buildWithCustomCancelAction(new BehaviorWithCustomCancelAction<>() {
                    @Override
                    public CustomCancelActionBehaviorResponse<Void> run(DependencyCallingDevice<ExampleMemory> device,
                            CompositeCancelSignal signal) {
                        Socket socket = new Socket();
                        CompletableFuture<Void> runSocketResponse = CompletableFuture.runAsync(socket::read);
                        CustomCancelAction action = mayInterrupt -> socket.close();
                        return new CustomCancelActionBehaviorResponse<>(runSocketResponse, action);
                    }

                    @Override
                    public boolean cancelActionMayInterruptIfRunning() {
                        // This node doesn't use interrupts for cancellation
                        return false;
                    }
                });
        Node.CommunalBuilder<ExampleMemory> entryNodeBuilder = Node.communalBuilder(ExampleMemory.class);
        SameMemoryDependency<ExampleMemory, Void> consumeReadFromSocket = entryNodeBuilder
                .sameMemoryUnprimedDependency(readFromSocket);
        ENTRY_NODE = entryNodeBuilder.type(Type.generic("ReadingAndCancelling"))
                .role(Role.of("ReadFromSocketAndCancel"))
                .build(device -> {
                    Reply<Void> socketReply = device.call(consumeReadFromSocket);
                    return socketReply.toCompletableFuture().orTimeout(200, TimeUnit.MILLISECONDS).exceptionally(t -> {
                        device.ignore(socketReply);
                        return socketReply.join();
                    });
                });

        // Create the Graph and a convenient GraphCall factory
        Graph<ExampleMemory> graph = Graph.fromRoots(Role.of("NonInterruptCustomAction"), Set.of(ENTRY_NODE));
        GRAPH_CALL_FACTORY = GraphCall.NoInputFactory.from(graph, ExampleMemory::new);
    }

    public static void main(String args[]) {
        Observer observer = Observer.doNothing(); // We don't want to observe any node calls
        GraphCall<ExampleMemory> graphCall = GRAPH_CALL_FACTORY.openCancellableCall(observer);
        long start = System.nanoTime();

        CompletableFuture<Reply<Void>> doneOrAbandonedReply = graphCall.finalCallAndWeaklyCloseOrAbandonOnTimeout(
                ENTRY_NODE, 5, TimeUnit.SECONDS, NonInterruptCustomAction::handleCallState);

        doneOrAbandonedReply.join();
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        System.out.println("Program done after " + duration.toMillis() + " ms");
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
