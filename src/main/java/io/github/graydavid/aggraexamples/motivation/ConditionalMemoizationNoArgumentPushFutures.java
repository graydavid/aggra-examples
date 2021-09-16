/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.motivation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.github.graydavid.aggraexamples.motivation.Types.GetTopLevelResponse;
import io.github.graydavid.aggraexamples.motivation.Types.Service1;
import io.github.graydavid.aggraexamples.motivation.Types.Service2;
import io.github.graydavid.aggraexamples.motivation.Types.ServiceA;
import io.github.graydavid.aggraexamples.motivation.Types.ServiceB;
import io.github.graydavid.aggraexamples.motivation.Types.ServiceResponse1;
import io.github.graydavid.aggraexamples.motivation.Types.ServiceResponse2;
import io.github.graydavid.aggraexamples.motivation.Types.ServiceResponseA;
import io.github.graydavid.aggraexamples.motivation.Types.ServiceResponseB;
import io.github.graydavid.aggraexamples.motivation.Types.TopLevelRequest;
import io.github.graydavid.aggraexamples.motivation.Types.TopLevelResponse;

public class ConditionalMemoizationNoArgumentPushFutures {
    private ConditionalMemoizationNoArgumentPushFutures() {}

    public static CompletableFuture<TopLevelResponse> run(Executor executor, TopLevelRequest request,
            boolean calculate2, boolean calculateB) {
        Supplier<CompletableFuture<ServiceResponse1>> unmemoizedResponseFuture1Supplier = () -> CompletableFuture
                .supplyAsync(() -> Service1.callService(request), executor);
        Supplier<CompletableFuture<ServiceResponse1>> memoizedResponseFuture1Supplier = memoize(
                unmemoizedResponseFuture1Supplier);

        CompletableFuture<ServiceResponse2> responseFuture2 = calculate2
                ? run2(executor, request, memoizedResponseFuture1Supplier)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<ServiceResponseB> responseFutureB = calculateB
                ? runB(executor, request, memoizedResponseFuture1Supplier)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Void> both2AndB = CompletableFuture.allOf(responseFuture2, responseFutureB);
        return both2AndB.thenApply(ignore -> {
            ServiceResponse2 response2 = responseFuture2.join();
            ServiceResponseB responseB = responseFutureB.join();
            return GetTopLevelResponse.getResponse(response2, responseB);
        });
    }

    private static CompletableFuture<ServiceResponse2> run2(Executor executor, TopLevelRequest request,
            Supplier<CompletableFuture<ServiceResponse1>> responseFuture1Supplier) {
        CompletableFuture<ServiceResponse1> responseFuture1 = responseFuture1Supplier.get();
        return responseFuture1.thenApply(response1 -> Service2.callService(response1));
    }

    private static CompletableFuture<ServiceResponseB> runB(Executor executor, TopLevelRequest request,
            Supplier<CompletableFuture<ServiceResponse1>> responseFuture1Supplier) {
        CompletableFuture<ServiceResponse1> responseFuture1 = responseFuture1Supplier.get();

        CompletableFuture<ServiceResponseA> responseFutureA = CompletableFuture
                .supplyAsync(() -> ServiceA.callService(request), executor);

        CompletableFuture<Void> both1AndA = CompletableFuture.allOf(responseFuture1, responseFutureA);
        return both1AndA.thenApply(ignore -> {
            ServiceResponse1 response1 = responseFuture1.join();
            ServiceResponseA responseA = responseFutureA.join();
            return ServiceB.callService(responseA, response1);
        });
    }

    private static <T> Supplier<T> memoize(Supplier<T> supplier) {
        // Placeholder: imagine we implement this to return a memoized version of supplier
        return null;
    }
}
