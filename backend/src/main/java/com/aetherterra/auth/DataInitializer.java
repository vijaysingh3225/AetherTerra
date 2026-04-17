package com.aetherterra.auth;

import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String ADMIN_EMAIL = "admin@aetherterra.com";
    private static final String ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            return;
        }
        var admin = new User();
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.save(admin);
        log.info("=================================================================");
        log.info("  Admin account created — email: {}  password: {}", ADMIN_EMAIL, ADMIN_PASSWORD);
        log.info("  Change this password before deploying to production.");
        log.info("=================================================================");
    }
}
