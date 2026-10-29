
package com.petdonation.service;

import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerAdopter(
            String fullName,
            String email,
            String rawPassword) {

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (rawPassword == null || rawPassword.length() < 12
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Use a password with at least 12 characters and no more than 72 UTF-8 bytes.");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                "An account with this email already exists."
            );
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("ADOPTER");

        return userRepository.save(user);
    }
}
