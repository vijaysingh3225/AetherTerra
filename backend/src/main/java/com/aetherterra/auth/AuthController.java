package com.aetherterra.auth;

import com.aetherterra.common.ApiResponse;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public AuthController(UserRepository userRepository,
                          EmailVerificationTokenRepository tokenRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @Transactional
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().toLowerCase().strip();

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.message("An account with that email already exists"));
        }

        var user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.BUYER);
        userRepository.save(user);

        String tokenValue = UUID.randomUUID().toString();
        var token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(tokenValue);
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        tokenRepository.save(token);

        try {
            emailService.sendVerificationEmail(email, tokenValue);
        } catch (Exception e) {
            log.warn("Failed to send verification email to {}: {}", email, e.getMessage());
        }

        return ResponseEntity.status(201)
                .body(ApiResponse.message("Account created. Check your email to verify your address."));
    }

    @Transactional
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        var verificationToken = tokenRepository.findByToken(token).orElse(null);

        if (verificationToken == null || verificationToken.getUsedAt() != null) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.message("Invalid or already used verification link"));
        }
        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.message("Verification link has expired. Please register again."));
        }

        verificationToken.setUsedAt(Instant.now());
        tokenRepository.save(verificationToken);

        User user = verificationToken.getUser();
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.message("Email verified. You can now sign in."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var user = userRepository.findByEmail(request.email().toLowerCase().strip()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(ApiResponse.message("Invalid email or password"));
        }
        String token = jwtUtil.generate(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(token, user.getEmail(), user.getRole().name())));
    }
}
