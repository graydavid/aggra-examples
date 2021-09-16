/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.motivation;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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

public class PullFuturesExtreme {
    private PullFuturesExtreme() {}

    public static Future<TopLevelResponse> run(ExecutorService executorService, TopLevelRequest request)
            throws InterruptedException, ExecutionException {
        Future<ServiceResponse1> responseFuture1 = executorService.submit(() -> Service1.callService(request));

        Future<ServiceResponseA> responseFutureA = executorService.submit(() -> ServiceA.callService(request));

        Future<ServiceResponse2> responseFuture2 = executorService.submit(() -> {
            ServiceResponse1 response1 = responseFuture1.get();
            return Service2.callService(response1);
        });

        Future<ServiceResponseB> responseFutureB = executorService.submit(() -> {
            ServiceResponse1 response1 = responseFuture1.get();
            ServiceResponseA responseA = responseFutureA.get();
            return ServiceB.callService(responseA, response1);
        });

        return executorService.submit(() -> {
            ServiceResponse2 response2 = responseFuture2.get();
            ServiceResponseB responseB = responseFutureB.get();
            return GetTopLevelResponse.getResponse(response2, responseB);
        });
    }
}
