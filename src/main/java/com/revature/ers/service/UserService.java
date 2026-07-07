package com.revature.ers.service;

import com.revature.ers.dto.ProfileUpdateRequest;
import com.revature.ers.dto.ProfileUpdateResult;
import com.revature.ers.model.User;
import com.revature.ers.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (form.currentPassword() == null
                || !passwordEncoder.matches(form.currentPassword(), user.getPassword())) {
            return ProfileUpdateResult.of(ProfileUpdateResult.Status.WRONG_PASSWORD);
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
        return new ProfileUpdateResult(ProfileUpdateResult.Status.UPDATED, userRepository.save(user));
    }
}
