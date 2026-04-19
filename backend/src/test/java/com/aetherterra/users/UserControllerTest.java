package com.aetherterra.users;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.auth.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwtUtil;

    private User user;
    private String token;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();

        user = new User();
        user.setEmail("profile@example.com");
        user.setPasswordHash(encoder.encode("secret123"));
        user.setEmailVerifiedAt(Instant.now());
        user = userRepository.save(user);

        token = jwtUtil.generate(user.getEmail(), user.getRole().name());
    }

    @Test
    void me_returnsCurrentUserProfile() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("profile@example.com"))
            .andExpect(jsonPath("$.data.emailVerified").value(true))
            .andExpect(jsonPath("$.data.paymentMethodBrand").isEmpty());
    }

    @Test
    void updateProfile_savesShirtSize() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("shirtSize", "XL"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.shirtSize").value("XL"));
    }

    @Test
    void updateProfile_invalidShirtSize_returns400() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("shirtSize", "XXXL"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void savePaymentMethod_persistsPlaceholderCard() throws Exception {
        mvc.perform(post("/api/v1/users/me/payment-method")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("brand", "Visa", "last4", "4242"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentMethodBrand").value("Visa"))
            .andExpect(jsonPath("$.data.paymentMethodLast4").value("4242"));
    }

    @Test
    void savePaymentMethod_invalidLast4_returns400() throws Exception {
        mvc.perform(post("/api/v1/users/me/payment-method")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("brand", "Visa", "last4", "42"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());
    }
}
