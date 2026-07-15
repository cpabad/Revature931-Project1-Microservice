package com.revature.ers.auth.service;

import com.revature.ers.auth.dto.ProfileUpdateRequest;
import com.revature.ers.auth.dto.ProfileUpdateResult;
import com.revature.ers.auth.event.UserProfileUpdatedEvent;
import com.revature.ers.auth.model.User;
import com.revature.ers.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * Profile self-service, ported from the monolith's UserService.updateProfile with the semantics
 * deliberately cleaned up (see ProfileUpdateResult): one current-password gate for the whole
 * form, explicit conflict outcomes instead of silent skips, and uniqueness checks that ignore
 * the caller's own row (re-submitting your current username is not a conflict).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Transactional
    public ProfileUpdateResult updateProfile(int userId, ProfileUpdateRequest form) {
        boolean wantsUsername = StringUtils.hasText(form.newUsername());
        boolean wantsEmail = StringUtils.hasText(form.newEmail());
        boolean wantsPassword = StringUtils.hasText(form.newPassword());
        if (!wantsUsername && !wantsEmail && !wantsPassword) {
            return ProfileUpdateResult.of(ProfileUpdateResult.Status.NOTHING_TO_UPDATE);
        }
        User user = userRepository.findById(userId).orElseThrow();
        // An oversized current password can never legitimately match a stored hash (hashes are of
        // <=72-byte passwords), and letting it reach BCrypt.matches() is exactly the CVE-2025-22228
        // hole - it could verify on a shared 72-byte prefix. Guard it into the same WRONG_PASSWORD
        // gate so an oversized value is simply a failed auth, indistinguishable from any other.
        // https://avd.aquasec.com/nvd/cve-2025-22228
        if (form.currentPassword() == null
                || exceedsBCrypt72ByteLimit(form.currentPassword())
                || !passwordEncoder.matches(form.currentPassword(), user.getPassword())) {
            return ProfileUpdateResult.of(ProfileUpdateResult.Status.WRONG_PASSWORD);
        }
        // A new password longer than BCrypt's 72-byte input would be silently truncated by encode()
        // (CVE-2025-22228), so we would store a hash of only its first 72 bytes. Reject the change
        // instead - checked here, before any mutation, so a rejected form changes nothing.
        // https://avd.aquasec.com/nvd/cve-2025-22228
        if (wantsPassword && exceedsBCrypt72ByteLimit(form.newPassword())) {
            return ProfileUpdateResult.of(ProfileUpdateResult.Status.NEW_PASSWORD_TOO_LONG);
        }
        // conflict checks BEFORE any mutation - a rejected form changes nothing
        if (wantsUsername && userRepository.existsByUsernameAndUserIdNot(form.newUsername(), userId)) {
            return ProfileUpdateResult.of(ProfileUpdateResult.Status.USERNAME_TAKEN);
        }
        if (wantsEmail && userRepository.existsByEmailAndUserIdNot(form.newEmail(), userId)) {
            return ProfileUpdateResult.of(ProfileUpdateResult.Status.EMAIL_TAKEN);
        }
        if (wantsUsername) {
            user.setUsername(form.newUsername());
        }
        if (wantsEmail) {
            user.setEmail(form.newEmail());
        }
        if (wantsPassword) {
            user.setPassword(passwordEncoder.encode(form.newPassword()));
        }
        User updated = userRepository.save(user);
        // Raised INSIDE the transaction; the Kafka publish happens at AFTER_COMMIT (see
        // UserUpdatedPublisher) - a rollback after this line publishes nothing.
        events.publishEvent(UserProfileUpdatedEvent.from(updated));
        return new ProfileUpdateResult(ProfileUpdateResult.Status.UPDATED, updated);
    }

    // BCrypt reads at most 72 bytes; spring-security-crypto silently truncates anything beyond
    // that (CVE-2025-22228). Byte length, not char length - a multi-byte UTF-8 character counts
    // once per byte, so the limit is reached sooner than the character count suggests.
    private static boolean exceedsBCrypt72ByteLimit(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
