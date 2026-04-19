package com.aetherterra.users;

import com.aetherterra.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        User user = findCurrentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.message("User not found"));
        }
        return ResponseEntity.ok(ApiResponse.ok(toProfile(user)));
    }

    @Transactional
    @PatchMapping("/me")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = findCurrentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.message("User not found"));
        }

        user.setShirtSize(request.shirtSize());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok(toProfile(user)));
    }

    @Transactional
    @PostMapping("/me/payment-method")
    public ResponseEntity<?> savePaymentMethod(
            Authentication authentication,
            @Valid @RequestBody SavePaymentMethodRequest request) {
        User user = findCurrentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.message("User not found"));
        }

        user.setPaymentMethodBrand(request.brand().trim());
        user.setPaymentMethodLast4(request.last4());
        user.setPaymentMethodAddedAt(Instant.now());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok(toProfile(user)));
    }

    private User findCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private UserProfileDto toProfile(User user) {
        return new UserProfileDto(
                user.getEmail(),
                user.getRole().name(),
                user.getShirtSize(),
                user.isEmailVerified(),
                user.getPaymentMethodBrand(),
                user.getPaymentMethodLast4(),
                user.getPaymentMethodAddedAt()
        );
    }
}
