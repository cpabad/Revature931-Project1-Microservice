package com.revature.ers.event;

/**
 * This service's OWN copy of the wire contract on {@code auth.user.updated} (the producer,
 * ers-auth-service, has its own identical record - same JSON field names, no shared library,
 * duplication over coupling). A full snapshot of the replicated user state: the consumer
 * overwrites its replica row from the event alone, no callback to auth-service. No password
 * field exists in this contract - the credential is structurally absent from this service's
 * world, events included.
 */
public record UserProfileUpdatedEvent(
        int userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String role) {
}
