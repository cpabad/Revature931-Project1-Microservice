package com.revature.ers.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The system's single front door (port 8080). Spring Cloud Gateway matches each request's path
 * against the routes in application.yml and proxies it to the owning service - the client never
 * learns the internal topology. The gateway does NOT touch authentication: the Bearer token
 * passes through untouched and each service validates it itself (defense stays with the owner;
 * a compromised gateway cannot mint identities).
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
