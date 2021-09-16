/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.cancellation;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
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
import io.github.graydavid.onemoretry.Try;

public class InterruptCustomAction {
    private InterruptCustomAction() {}

    // Every Graph needs a Memory subclass
    private static class ExampleMemory extends Memory<Void> {
        private ExampleMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static final Node<ExampleMemory, Boolean> ENTRY_NODE;
    private static final GraphCall.NoInputFactory<ExampleMemory> GRAPH_CALL_FACTORY;
    static {
        // Create an executor using daemon threads so that it won't block program shutdown
        Executor executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
        });

        // Create the nodes in the graph
        Node<ExampleMemory, Boolean> awaitNeverCountDownLatch = Node.communalBuilder(ExampleMemory.class)
                .type(Type.generic("NeverCountDownLatchReading"))
                .role(Role.of("AwaitNeverCountDownLatch"))
                .graphValidatorFactory(GraphValidators.ignoringWillTriggerReplyCancelSignal())
                .buildWithCustomCancelAction(new BehaviorWithCustomCancelAction<>() {
                    @Override
                    public CustomCancelActionBehaviorResponse<Boolean> run(
                            DependencyCallingDevice<ExampleMemory> device, CompositeCancelSignal signal) {
                        CompletableFuture<Boolean> response = new CompletableFuture<>();
                        CompletingOnCancelFutureTask<?> task = new CompletingOnCancelFutureTask<>(() -> {
                            awaitNeverCountDownLatchTowardsFuture(response);
                            return null;
                        }, response);
                        executor.execute(task);
                        CustomCancelAction action = mayInterrupt -> task.cancel(mayInterrupt);
                        return new CustomCancelActionBehaviorResponse<>(response, action);
                    }

                    private void awaitNeverCountDownLatchTowardsFuture(CompletableFuture<Boolean> futureToComplete) {
                        Try.callCatchThrowable(() -> {
                            CountDownLatch latch = new CountDownLatch(1);
                            return latch.await(1, TimeUnit.SECONDS);
                        }).consume((bool, t) -> {
                            if (t == null) {
                                futureToComplete.complete(bool);
                            } else {
                                futureToComplete.completeExceptionally(t);
                            }
                        });
                    }

                    @Override
                    public boolean cancelActionMayInterruptIfRunning() {
                        // This node does use interrupts for cancellation
                        return true;
                    }
                });
        Node.CommunalBuilder<ExampleMemory> entryNodeBuilder = Node.communalBuilder(ExampleMemory.class);
        SameMemoryDependency<ExampleMemory, Boolean> consumeAwaitNeverLatch = entryNodeBuilder
                .sameMemoryUnprimedDependency(awaitNeverCountDownLatch);
        ENTRY_NODE = entryNodeBuilder.type(Type.generic("AwaitingAndCancelling"))
                .role(Role.of("AwaitNeverCountDownLatchAndCancel"))
                .build(device -> {
                    Reply<Boolean> awaitReply = device.call(consumeAwaitNeverLatch);
                    return awaitReply.toCompletableFuture().orTimeout(200, TimeUnit.MILLISECONDS).exceptionally(t -> {
                        device.ignore(awaitReply);
                        return awaitReply.join();
                    });
                });

        // Create the Graph and a convenient GraphCall factory
        Graph<ExampleMemory> graph = Graph.fromRoots(Role.of("InterruptCustomAction"), Set.of(ENTRY_NODE));
        GRAPH_CALL_FACTORY = GraphCall.NoInputFactory.from(graph, ExampleMemory::new);
    }

    /**
     * Completes a provided CompletableFuture if this task is cancelled. This helps make sure the CompletableFuture
     * completes both on normal running and cancellation of the task.
     */
    private static class CompletingOnCancelFutureTask<T> extends FutureTask<T> {
        private final CompletableFuture<?> futureToComplete;

        private CompletingOnCancelFutureTask(Callable<T> callable, CompletableFuture<?> futureToComplete) {
            super(callable);
            this.futureToComplete = futureToComplete;
        }

        @Override
        protected void done() {
            if (isCancelled()) {
                futureToComplete.completeExceptionally(new CancellationException());
            }
        }
    }

    public static void main(String args[]) {
        Observer observer = Observer.doNothing(); // We don't want to observe any node calls
        GraphCall<ExampleMemory> graphCall = GRAPH_CALL_FACTORY.openCancellableCall(observer);
        long start = System.nanoTime();

        CompletableFuture<Reply<Boolean>> doneOrAbandonedReply = graphCall.finalCallAndWeaklyCloseOrAbandonOnTimeout(
                ENTRY_NODE, 5, TimeUnit.SECONDS, InterruptCustomAction::handleCallState);

        doneOrAbandonedReply.join();
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        System.out.println("Program done after " + duration.toMillis() + " ms");
    }

    private static void handleCallState(GraphCall.State state, Throwable throwable, Reply<Boolean> finalReply) {
        if (state.isAbandoned()) {
            System.out.println(
                    "Error: we had to abandon graph call. Some processes may still be running in the background");
        }

        if (throwable == null) {
            if (!state.getUnhandledExceptions().isEmpty()) {
                System.out.println("Unhandled Exceptions:");
            }
            state.getUnhandledExceptions().stream().forEach(t -> t.printStackTrace());
        } else {
            throwable.printStackTrace();
        }
    }
}
