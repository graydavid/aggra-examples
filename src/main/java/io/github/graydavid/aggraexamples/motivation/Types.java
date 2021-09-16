/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.aggraexamples.motivation;

public class Types {
    private Types() {}

    public static class TopLevelRequest {
    }
    public static class TopLevelResponse {
    }
    public static class ServiceResponse1 {
    }
    public static class ServiceResponse2 {
    }
    public static class ServiceResponseA {
    }
    public static class ServiceResponseB {
    }
    public static class Service1 {
        static ServiceResponse1 callService(TopLevelRequest request) {
            return new ServiceResponse1();
        }
    }
    public static class Service2 {
        static ServiceResponse2 callService(ServiceResponse1 response1) {
            return new ServiceResponse2();
        }
    }
    public static class ServiceA {
        static ServiceResponseA callService(TopLevelRequest request) {
            return new ServiceResponseA();
        }
    }
    public static class ServiceB {
        static ServiceResponseB callService(ServiceResponseA responseA, ServiceResponse1 response1) {
            return new ServiceResponseB();
        }
    }
    public static class GetTopLevelResponse {
        static TopLevelResponse getResponse(ServiceResponse2 response2, ServiceResponseB responseB) {
            return new TopLevelResponse();
        }
    }
}
