package com.revature.ers.soap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Anti-corruption layer for legacy partners that speak SOAP. This service exists to keep the
 * legacy protocol OUT of the domain services: it accepts a SOAP submission, translates it into
 * a plain JSON event on Kafka, and acknowledges. No database, no domain rules - if SOAP ever
 * dies, this module is deleted and nothing else changes.
 */
@SpringBootApplication
public class SoapAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoapAdapterApplication.class, args);
    }
}
