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

public class PushFutures {
    private PushFutures() {}

    public static CompletableFuture<TopLevelResponse> run(Executor executor, TopLevelRequest request) {
        CompletableFuture<ServiceResponse1> responseFuture1 = CompletableFuture
                .supplyAsync(() -> Service1.callService(request), executor);

        CompletableFuture<ServiceResponseA> responseFutureA = CompletableFuture
                .supplyAsync(() -> ServiceA.callService(request), executor);

        CompletableFuture<ServiceResponse2> responseFuture2 = responseFuture1
                .thenApply(response1 -> Service2.callService(response1));

        CompletableFuture<Void> both1AndA = CompletableFuture.allOf(responseFuture1, responseFutureA);
        CompletableFuture<ServiceResponseB> responseFutureB = both1AndA.thenApply(ignore -> {
            ServiceResponse1 response1 = responseFuture1.join();
            ServiceResponseA responseA = responseFutureA.join();
            return ServiceB.callService(responseA, response1);
        });

        CompletableFuture<Void> both2AndB = CompletableFuture.allOf(responseFuture2, responseFutureB);
        return both2AndB.thenApply(ignore -> {
            ServiceResponse2 response2 = responseFuture2.join();
            ServiceResponseB responseB = responseFutureB.join();
            return GetTopLevelResponse.getResponse(response2, responseB);
        });
    }
}
