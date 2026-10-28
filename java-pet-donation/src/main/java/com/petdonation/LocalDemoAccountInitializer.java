package com.petdonation;

import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
public class LocalDemoAccountInitializer {

    @Bean
    CommandLineRunner createLocalDemoAccount(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> userRepository.findByEmail("demo@pawprints.test")
                .orElseGet(() -> {
                    User user = new User();
                    user.setFullName("Pawprints Demo");
                    user.setEmail("demo@pawprints.test");
                    user.setPassword(passwordEncoder.encode("Pawprints123!"));
                    user.setRole("ADOPTER");
                    return userRepository.save(user);
                });
    }
}
