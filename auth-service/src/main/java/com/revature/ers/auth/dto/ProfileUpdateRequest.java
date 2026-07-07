package com.revature.ers.auth.dto;

/**
 * Inbound PUT /users/me body. currentPassword is required for ANY change (you re-prove your
 * identity to alter it - stronger than the monolith, which only demanded the old value of the
 * field being changed); the three new* fields are each optional, blank/null = leave unchanged.
 */
public record ProfileUpdateRequest(String currentPassword, String newUsername, String newEmail, String newPassword) {
}
