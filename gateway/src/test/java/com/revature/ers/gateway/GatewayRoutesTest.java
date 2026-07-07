package com.revature.ers.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway needs no database - this test just proves the context starts and the two routes
 * exist and point at the right services. (True end-to-end routing needs all three apps up;
 * that's the manual smoke test in the README.)
 */
@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void definesAllServiceRoutes() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        List<String> ids = Objects.requireNonNull(routes).stream().map(Route::getId).toList();

        assertEquals(3, routes.size());
        assertTrue(ids.contains("auth-service"));
        assertTrue(ids.contains("reimbursement-service"));
        assertTrue(ids.contains("soap-adapter"));
        assertTrue(routes.stream().anyMatch(r -> r.getUri().toString().contains("8081")));
        assertTrue(routes.stream().anyMatch(r -> r.getUri().toString().contains("8082")));
        assertTrue(routes.stream().anyMatch(r -> r.getUri().toString().contains("8083")));
    }
}
