/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.motivation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

public class ConditionalMemoizationWithArgumentsPushFutures {
    private ConditionalMemoizationWithArgumentsPushFutures() {}

    public static CompletableFuture<TopLevelResponse> run(Executor executor, TopLevelRequest request,
            boolean calculate2, boolean calculateB) {
        ConcurrentHashMap<Object, Object> memory = new ConcurrentHashMap<>();

        CompletableFuture<ServiceResponse2> responseFuture2 = calculate2 ? run2(executor, request, memory)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<ServiceResponseB> responseFutureB = calculateB ? runB(executor, request, memory)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Void> both2AndB = CompletableFuture.allOf(responseFuture2, responseFutureB);
        return both2AndB.thenApply(ignore -> {
            ServiceResponse2 response2 = responseFuture2.join();
            ServiceResponseB responseB = responseFutureB.join();
            return GetTopLevelResponse.getResponse(response2, responseB);
        });
    }

    private static CompletableFuture<ServiceResponse2> run2(Executor executor, TopLevelRequest request,
            ConcurrentHashMap<Object, Object> memory) {
        CompletableFuture<ServiceResponse1> responseFuture1 = computeIfAbsent(memory,
                createKey("Service1#callService", request),
                () -> CompletableFuture.supplyAsync(() -> Service1.callService(request), executor));

        return responseFuture1.thenApply(response1 -> Service2.callService(response1));
    }

    private static CompletableFuture<ServiceResponseB> runB(Executor executor, TopLevelRequest request,
            ConcurrentHashMap<Object, Object> memory) {
        CompletableFuture<ServiceResponse1> responseFuture1 = computeIfAbsent(memory,
                createKey("Service1#callService", request),
                () -> CompletableFuture.supplyAsync(() -> Service1.callService(request), executor));

        CompletableFuture<ServiceResponseA> responseFutureA = CompletableFuture
                .supplyAsync(() -> ServiceA.callService(request), executor);

        CompletableFuture<Void> both1AndA = CompletableFuture.allOf(responseFuture1, responseFutureA);
        return both1AndA.thenApply(ignore -> {
            ServiceResponse1 response1 = responseFuture1.join();
            ServiceResponseA responseA = responseFutureA.join();
            return ServiceB.callService(responseA, response1);
        });
    }

    // Suppress justification: we only insert values into the map if they're compatible with T
    @SuppressWarnings("unchecked")
    private static <T> T computeIfAbsent(ConcurrentHashMap<Object, Object> memory, Object key, Supplier<T> supplier) {
        return (T) memory.computeIfAbsent(key, ignore -> supplier.get());
    }

    private static Object createKey(String ClassAndMethod, Object... arguments) {
        // Placeholder: imagine we implement this to return a unique key given the arguments
        return null;
    }
}
