package com.revature.ers.auth.event;

import com.revature.ers.auth.model.User;

/**
 * Published on {@code auth.user.updated} after a profile update COMMITS - this service's copy
 * of the wire contract (consumers own their own identical record; same JSON field names, no
 * shared library, duplication over coupling).
 *
 * Event-carried state transfer: the event carries the FULL replicated state, not a delta and
 * not just an id. A consumer overwrites its replica row from the event alone - no callback to
 * this service (an id-only notification would reintroduce the runtime coupling events exist to
 * remove), and applying it twice, or applying a newer one, converges to the same row. Note
 * what is absent: the password. It is not replicated anywhere and never rides an event.
 */
public record UserProfileUpdatedEvent(
        int userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String role) {

    public static UserProfileUpdatedEvent from(User user) {
        return new UserProfileUpdatedEvent(user.getUserId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getRole().getRole());
    }
}
