/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.advanced;

import java.util.concurrent.CompletableFuture;

public class NeverComplete {
    private NeverComplete() {}

    public static void main(String args[]) {
        run();
    }

    private static CompletableFuture<Integer> run() {
        CompletableFuture<Integer> response = new CompletableFuture<>();

        // If doSomething fails, thenRun will *never* run its supplied completing function
        CompletableFuture.runAsync(NeverComplete::doSomething).thenRun(() -> response.complete(5));

        // If doSomething fails, whenComplete *will* run its supplied completing function
        CompletableFuture.runAsync(NeverComplete::doSomething).whenComplete((r, t) -> response.complete(5));

        // If doSomething fails, whenComplete will run, but what if doSomethingElse throws
        CompletableFuture.runAsync(NeverComplete::doSomething).whenComplete((r, t) -> {
            doSomethingElse();
            response.complete(5);
        });

        return response;
    }

    private static void doSomething() {}

    private static void doSomethingElse() {}
}
