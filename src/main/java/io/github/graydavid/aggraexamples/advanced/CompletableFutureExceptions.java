/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.github.graydavid.onemoretry.Try;

public class CompletableFutureExceptions {
    private CompletableFutureExceptions() {}

    public static void main(String args[]) {
        IllegalArgumentException exception = new IllegalArgumentException();

        System.out.println("Constructed");
        CompletableFuture<Integer> constructed = CompletableFuture.failedFuture(exception);
        reportAccessPatterns(constructed);

        System.out.println("\nThrown");
        CompletableFuture<Integer> thrown = CompletableFuture.supplyAsync(() -> {
            throw exception;
        });
        reportAccessPatterns(thrown);
    }

    private static void reportAccessPatterns(CompletableFuture<?> future) {
        future.whenComplete((r, t) -> System.out.println("Chained: " + causes(t)));
        Try.callCatchRuntime(future::join).getFailure().ifPresent(t -> System.out.println("Joined: " + causes(t)));
        Try.callCatchException(future::get).getFailure().ifPresent(t -> System.out.println("Gotten: " + causes(t)));
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
