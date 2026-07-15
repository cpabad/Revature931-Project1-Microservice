package com.revature.ers.auth.dto;

import com.revature.ers.auth.model.User;

/**
 * What a profile update settled. Deliberately CLEANER than the monolith's semantics: there, a
 * taken username/email was silently skipped and the update still reported success (a quirk its
 * tests pin); here a taken value is an explicit outcome the controller turns into a 409.
 * {@code user} is non-null only for UPDATED.
 *
 * NEW_PASSWORD_TOO_LONG is the honest outcome for a new password over BCrypt's 72-byte input
 * limit (CVE-2025-22228): the client sent something we cannot store without silent truncation, so
 * the controller answers 400 rather than pretending success or blaming the current password.
 */
public record ProfileUpdateResult(Status status, User user) {

    public enum Status { UPDATED, WRONG_PASSWORD, USERNAME_TAKEN, EMAIL_TAKEN, NOTHING_TO_UPDATE, NEW_PASSWORD_TOO_LONG }

    public static ProfileUpdateResult of(Status status) {
        return new ProfileUpdateResult(status, null);
    }
}
