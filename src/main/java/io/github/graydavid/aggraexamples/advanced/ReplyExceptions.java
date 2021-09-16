/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.github.graydavid.aggra.core.CallObservers.Observer;
import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Reply;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.CompletionFunctionNodes;
import io.github.graydavid.onemoretry.Try;

public class ReplyExceptions {
    private ReplyExceptions() {}

    private static class ExceptionMemory extends Memory<String> {
        private ExceptionMemory(MemoryScope scope) {
            super(scope, CompletableFuture.completedFuture(null), Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        IllegalArgumentException exception = new IllegalArgumentException();

        System.out.println("Constructed");
        Node<ExceptionMemory, Integer> constructFailure = CompletionFunctionNodes
                .threadLingering(Role.of("ConstructFailure"), ExceptionMemory.class)
                .get(() -> CompletableFuture.failedFuture(exception));
        Reply<Integer> constructed = constructGraphAndCall(constructFailure);
        reportAccessPatterns(constructed);

        System.out.println("\nThrown");
        Node<ExceptionMemory, Integer> throwFailure = CompletionFunctionNodes
                .threadLingering(Role.of("ThrowFailure"), ExceptionMemory.class)
                .get(() -> CompletableFuture.supplyAsync(() -> {
                    throw exception;
                }));
        Reply<Integer> thrown = constructGraphAndCall(throwFailure);
        reportAccessPatterns(thrown);
    }

    private static Reply<Integer> constructGraphAndCall(Node<ExceptionMemory, Integer> node) {
        Graph<ExceptionMemory> graph = Graph.fromRoots(Role.of("TemporaryGraph"), Set.of(node));
        return graph.openCancellableCall(ExceptionMemory::new, Observer.doNothing()).call(node);
    }

    private static void reportAccessPatterns(Reply<?> reply) {
        reply.whenComplete((r, t) -> System.out.println("Chained: " + causes(t)));
        Try.callCatchRuntime(reply::join).getFailure().ifPresent(t -> System.out.println("Joined: " + causes(t)));
        Try.callCatchException(reply::get).getFailure().ifPresent(t -> System.out.println("Gotten: " + causes(t)));
    }

    private static List<Class<? extends Throwable>> causes(Throwable t) {
        List<Class<? extends Throwable>> causes = new ArrayList<>();
        Throwable current = t;
        do {
            causes.add(current.getClass());
            current = current.getCause();
        } while (current != null);
        return causes;
    }
}
