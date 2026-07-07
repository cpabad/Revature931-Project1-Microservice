package com.revature.ers.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the identity service: login (the JWT issuer), profile self-service, and the
 * roles reference data. This service OWNS the users/roles tables; everything else in the system
 * treats them as read-only reference data.
 */
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
