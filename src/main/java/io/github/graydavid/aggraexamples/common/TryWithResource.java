/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import io.github.graydavid.aggra.core.ConcurrentHashMapStorage;
import io.github.graydavid.aggra.core.DependencyLifetime;
import io.github.graydavid.aggra.core.Memory;
import io.github.graydavid.aggra.core.MemoryScope;
import io.github.graydavid.aggra.core.Node;
import io.github.graydavid.aggra.core.Role;
import io.github.graydavid.aggra.nodes.FunctionNodes;
import io.github.graydavid.aggra.nodes.TryWithResourceNodes;

public class TryWithResource {
    private TryWithResource() {}

    private static class TestMemory extends Memory<String> {
        private TestMemory(MemoryScope scope, CompletionStage<String> input) {
            super(scope, input, Set.of(), () -> new ConcurrentHashMapStorage());
        }
    }

    public static void main(String args[]) {
        Node<TestMemory, String> getInput = Node.inputBuilder(TestMemory.class).role(Role.of("GetInput")).build();
        Node<TestMemory, String> safelyReadFirstLineOfFile = TryWithResourceNodes
                .startNode(Role.of("SafelyReadFirstLineOfFile"), TestMemory.class)
                .tryWith(() -> openFile(getInput), TryWithResource::readFirstLineOfFile);
        System.out.println(safelyReadFirstLineOfFile);
    }

    private static Node<TestMemory, BufferedReader> openFile(Node<TestMemory, String> getInput) {
        return FunctionNodes.synchronous(Role.of("OpenFile"), TestMemory.class)
                .graphValidatorFactory(TryWithResourceNodes.validateResourceConsumedByTryWithResource())
                .apply(name -> callUnchecked(() -> Files.newBufferedReader(Path.of(name))), getInput);
    }

    private static Node<TestMemory, String> readFirstLineOfFile(Node<TestMemory, BufferedReader> openFile) {
        return FunctionNodes.synchronous(Role.of("ReadFirstLineOfFile"), TestMemory.class)
                .dependencyLifetime(DependencyLifetime.NODE_FOR_ALL)
                .apply(reader -> callUnchecked(() -> reader.readLine()), openFile);
    }

    private interface IoCallable<T> {
        T call() throws IOException;
    }

    // "Deal" with checked IOExceptions
    private static <T> T callUnchecked(IoCallable<T> callable) {
        try {
            return callable.call();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
