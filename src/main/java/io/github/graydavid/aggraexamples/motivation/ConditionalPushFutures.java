/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.motivation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

public class ConditionalPushFutures {
    private ConditionalPushFutures() {}

    public static CompletableFuture<TopLevelResponse> run(Executor executor, TopLevelRequest request,
            boolean calculate2, boolean calculateB) {
        boolean calculate1 = calculate2 || calculateB;
        CompletableFuture<ServiceResponse1> responseFuture1 = calculate1
                ? CompletableFuture.supplyAsync(() -> Service1.callService(request), executor)
                : CompletableFuture.completedFuture(null);

        boolean calculateA = calculateB;
        CompletableFuture<ServiceResponseA> responseFutureA = calculateA
                ? CompletableFuture.supplyAsync(() -> ServiceA.callService(request), executor)
                : CompletableFuture.completedFuture(null);

        CompletableFuture<ServiceResponse2> responseFuture2 = calculate2
                ? responseFuture1.thenApply(response1 -> Service2.callService(response1))
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Void> both1AndA = CompletableFuture.allOf(responseFuture1, responseFutureA);
        CompletableFuture<ServiceResponseB> responseFutureB = calculateB ? both1AndA.thenApply(ignore -> {
            ServiceResponse1 response1 = responseFuture1.join();
            ServiceResponseA responseA = responseFutureA.join();
            return ServiceB.callService(responseA, response1);
        }) : CompletableFuture.completedFuture(null);

        CompletableFuture<Void> both2AndB = CompletableFuture.allOf(responseFuture2, responseFutureB);
        return both2AndB.thenApply(ignore -> {
            ServiceResponse2 response2 = responseFuture2.join();
            ServiceResponseB responseB = responseFutureB.join();
            return GetTopLevelResponse.getResponse(response2, responseB);
        });
    }
}
