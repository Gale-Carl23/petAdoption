package com.petdonation;

import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Optional;

/** Creates the initial production administrator once, when the database is empty of that account. */
@Component
@Profile("production")
public class AdminAccountBootstrap implements CommandLineRunner {

    private static final long BOOTSTRAP_LOCK_ID = 728194631L;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountBootstrap(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String email,
            @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String password) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() || email.length() > 150
                || password == null || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Production requires a valid ADMIN_BOOTSTRAP_EMAIL and a 16–72 byte ADMIN_BOOTSTRAP_PASSWORD.");
        }

        transactionTemplate.executeWithoutResult(status -> {
            acquireBootstrapLock();
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                if (!"ADMIN".equals(existing.get().getRole())) {
                    throw new IllegalStateException(
                            "The configured bootstrap email already belongs to a non-admin account.");
                }
                return;
            }

            User administrator = new User();
            administrator.setFullName("Administrator");
            administrator.setEmail(email);
            administrator.setPassword(passwordEncoder.encode(password));
            administrator.setRole("ADMIN");
            userRepository.saveAndFlush(administrator);
        });
    }

    private void acquireBootstrapLock() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, BOOTSTRAP_LOCK_ID);
                statement.execute();
            }
            return null;
        });
    }
}
