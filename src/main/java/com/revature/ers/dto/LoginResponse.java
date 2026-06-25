package com.revature.ers.dto;

/**
 * Outbound /login body on success. The token is the credential the client stores and replays as
 * "Authorization: Bearer <token>" on later calls. userId/role are echoed for convenience (they
 * also live inside the token's claims); expiresInSeconds lets a client know when to re-login.
 * The password is, of course, never echoed back.
 */
public record LoginResponse(String token, String tokenType, Integer userId, String role, long expiresInSeconds) {
}
