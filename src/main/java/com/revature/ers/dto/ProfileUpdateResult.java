package com.revature.ers.dto;

import com.revature.ers.model.User;

/**
 * What a profile update settled. Deliberately CLEANER than the monolith's semantics: there, a
 * taken username/email was silently skipped and the update still reported success (a quirk its
 * tests pin); here a taken value is an explicit outcome the controller turns into a 409.
 * {@code user} is non-null only for UPDATED.
 */
public record ProfileUpdateResult(Status status, User user) {

    public enum Status { UPDATED, WRONG_PASSWORD, USERNAME_TAKEN, EMAIL_TAKEN, NOTHING_TO_UPDATE }

    public static ProfileUpdateResult of(Status status) {
        return new ProfileUpdateResult(status, null);
    }
}
