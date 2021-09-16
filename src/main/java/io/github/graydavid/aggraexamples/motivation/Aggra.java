/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.motivation;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.github.graydavid.aggra.core.CallObservers.Observer;
import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.Graph;
import io.github.graydavid.aggra.core.GraphCall;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Reply;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.FunctionNodes.CreationTimeExecutorAsynchronousStarter;
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

public class Aggra {
    private Aggra() {}

    // Create the Graph and nodes (static is used for this example; Spring/Guice are just as valid)
    private static class ServiceOperationMemory extends Memory<TopLevelRequest> {
        // Every Graph needs a Memory
        private ServiceOperationMemory(MemoryScope scope, CompletionStage<TopLevelRequest> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    private static final Node<ServiceOperationMemory, TopLevelResponse> GET_TOP_LEVEL_RESPONSE;
    private static final GraphCall.Factory<TopLevelRequest, ServiceOperationMemory> GRAPH_CALL_FACTORY;
    static {
        // Create an easy way to create asynchronous FunctionNodes
        Executor asynchronousExecutor = Executors.newCachedThreadPool();
        CreationTimeExecutorAsynchronousStarter asychronousStarter = CreationTimeExecutorAsynchronousStarter
                .from(asynchronousExecutor);

        // Create the nodes in the graph
        Node<ServiceOperationMemory, TopLevelRequest> getTopLevelRequest = Node
                .inputBuilder(ServiceOperationMemory.class)
                .role(Role.of("GetTopLevelRequest"))
                .build();
        Node<ServiceOperationMemory, ServiceResponse1> callService1 = asychronousStarter
                .startNode(Role.of("CallService1"), ServiceOperationMemory.class)
                .apply(Service1::callService, getTopLevelRequest);
        Node<ServiceOperationMemory, ServiceResponse2> callService2 = asychronousStarter
                .startNode(Role.of("CallService2"), ServiceOperationMemory.class)
                .apply(Service2::callService, callService1);
        Node<ServiceOperationMemory, ServiceResponseA> callServiceA = asychronousStarter
                .startNode(Role.of("CallServiceA"), ServiceOperationMemory.class)
                .apply(ServiceA::callService, getTopLevelRequest);
        Node<ServiceOperationMemory, ServiceResponseB> callServiceB = asychronousStarter
                .startNode(Role.of("CallServiceB"), ServiceOperationMemory.class)
                .apply(ServiceB::callService, callServiceA, callService1);
        GET_TOP_LEVEL_RESPONSE = FunctionNodes.synchronous(Role.of("GetTopLevelResponse"), ServiceOperationMemory.class)
                .apply(GetTopLevelResponse::getResponse, callService2, callServiceB);

        // Create the Graph and a convenient GraphCall factory
        Graph<ServiceOperationMemory> graph = Graph.fromRoots(Role.of("ServiceOperationGraph"),
                Set.of(GET_TOP_LEVEL_RESPONSE));
        GRAPH_CALL_FACTORY = GraphCall.Factory.from(graph, ServiceOperationMemory::new);
    }

    public TopLevelResponse getResponse(TopLevelRequest request) {
        GraphCall<ServiceOperationMemory> graphCall = GRAPH_CALL_FACTORY
                .openCancellableCall(CompletableFuture.completedFuture(request), Observer.doNothing());
        Reply<TopLevelResponse> response = graphCall.call(GET_TOP_LEVEL_RESPONSE);
        CompletableFuture<GraphCall.FinalState> finalCallState = graphCall.weaklyClose();
        finalCallState.join();
        // Do any logging of ignoredReplies or unhandledExceptions in the finalCallState
        return response.join();
    }
}
