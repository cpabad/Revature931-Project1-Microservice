package com.revature.ers.soap;

import com.revature.ers.soap.security.PartnerAllowlist;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Anti-corruption layer for legacy partners that speak SOAP. This service exists to keep the
 * legacy protocol OUT of the domain services: it accepts a SOAP submission, translates it into
 * a plain JSON event on Kafka, and acknowledges. No database, no domain rules - if SOAP ever
 * dies, this module is deleted and nothing else changes.
 */
@SpringBootApplication
@EnableConfigurationProperties(PartnerAllowlist.class)
public class SoapAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoapAdapterApplication.class, args);
    }

    /** Say the trust posture out loud at startup - nobody should discover it in an incident. */
    @EventListener(ApplicationReadyEvent.class)
    public void announceTrustPosture(ApplicationReadyEvent ready) {
        Environment env = ready.getApplicationContext().getEnvironment();
        boolean mtls = env.matchesProfiles("mtls");
        LoggerFactory.getLogger(SoapAdapterApplication.class).warn(mtls
                ? "PARTNER AUTH ON (mtls profile): client certificates required; allowlist enforced per partner CN"
                : "PARTNER AUTH OFF (dev profile): callers are unauthenticated and requesterUserId is TRUSTED - never expose this mode to partner traffic");
    }
}
