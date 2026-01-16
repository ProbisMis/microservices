package org.programming.microservices.apigateway.routes;

import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@COnfiguration
public class Routes {

    @Bean
    public RouterFunction<ServerResponse> productServiceRoute() {
        return GatewayRouterFunctions
            .route("product-service")
            .route(RequestPredicates.path("/api/product"), 
                HandlerFunctions.http("http://localhost:8080")).build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderServiceRoute() {
        return GatewayRouterFunctions
            .route("order-service")
            .route(RequestPredicates.path("/api/order"), 
                HandlerFunctions.http("http://localhost:8081")).build();
    }

    @Bean
    public RouterFunction<ServerResponse> inventoryServiceRoute() {
        return GatewayRouterFunctions
            .route("inventory-service")
            .route(RequestPredicates.path("/api/inventory"), 
                HandlerFunctions.http("http://localhost:8082")).build();
    }
}
