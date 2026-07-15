package com.revature.ers.auth.controller;

import com.revature.ers.auth.dto.ProfileUpdateRequest;
import com.revature.ers.auth.dto.ProfileUpdateResult;
import com.revature.ers.auth.model.User;
import com.revature.ers.auth.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile self-service. /users/me only - the identity comes from the token's subject, so there
 * is no "update someone else's profile by changing an id" surface at all.
 *
 *   PUT /users/me  -> 200 updated user | 400 nothing to update / new password too long
 *                     | 403 wrong current password | 409 username/email taken
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/users/me")
    public ResponseEntity<User> updateMyProfile(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody ProfileUpdateRequest form) {
        ProfileUpdateResult result = userService.updateProfile(Integer.parseInt(jwt.getSubject()), form);
        // Java 17 switch EXPRESSION over the enum: exhaustive (the compiler proves every status
        // is handled - add a status and this fails to compile until it's mapped) and value-yielding.
        return switch (result.status()) {
            case UPDATED -> ResponseEntity.ok(result.user());
            // Bad request: either nothing was submitted, or the new password exceeds BCrypt's
            // 72-byte input limit (CVE-2025-22228) and cannot be stored without silent truncation.
            case NOTHING_TO_UPDATE, NEW_PASSWORD_TOO_LONG -> ResponseEntity.badRequest().build();
            case WRONG_PASSWORD -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            case USERNAME_TAKEN, EMAIL_TAKEN -> ResponseEntity.status(HttpStatus.CONFLICT).build();
        };
    }
}
