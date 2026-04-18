package com.aetherterra.auth;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.auctions.AuctionRepository;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepo;
    @Autowired EmailVerificationTokenRepository tokenRepo;
    @Autowired AuctionRepository auctionRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        auctionRepo.deleteAll();
        tokenRepo.deleteAll();
        userRepo.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    @Test
    void register_success_returns201() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "alice@example.com", "password", "secret123"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("Account created. Check your email to verify your address."));

        assertThat(userRepo.findByEmail("alice@example.com")).isPresent();
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        doRegister("bob@example.com");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "bob@example.com", "password", "secret123"))))
            .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "not-an-email", "password", "secret123"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "carol@example.com", "password", "short"))))
            .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Email verification
    // -------------------------------------------------------------------------

    @Test
    void verifyEmail_validToken_marksUserVerified() throws Exception {
        doRegister("dave@example.com");
        String token = tokenRepo.findAll().get(0).getToken();

        mvc.perform(get("/api/v1/auth/verify-email").param("token", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Email verified. You can now sign in."));

        assertThat(userRepo.findByEmail("dave@example.com").get().getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void verifyEmail_unknownToken_returns400() throws Exception {
        mvc.perform(get("/api/v1/auth/verify-email").param("token", "made-up-token"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_expiredToken_returns400() throws Exception {
        User user = saveUnverifiedUser("expired@example.com");

        var token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken("expired-token-abc");
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        tokenRepo.save(token);

        mvc.perform(get("/api/v1/auth/verify-email").param("token", "expired-token-abc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_alreadyUsedToken_returns400() throws Exception {
        doRegister("frank@example.com");
        String token = tokenRepo.findAll().get(0).getToken();

        mvc.perform(get("/api/v1/auth/verify-email").param("token", token))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/verify-email").param("token", token))
            .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Test
    void login_verifiedUser_returnsJwt() throws Exception {
        doRegisterAndVerify("grace@example.com");

        String body = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "grace@example.com", "password", "secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists())
            .andExpect(jsonPath("$.data.email").value("grace@example.com"))
            .andExpect(jsonPath("$.data.role").value("BUYER"))
            .andReturn().getResponse().getContentAsString();

        String jwt = om.readTree(body).path("data").path("token").asText();
        assertThat(jwtUtil.isValid(jwt)).isTrue();
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        doRegisterAndVerify("henry@example.com");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "henry@example.com", "password", "wrongpass"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unverifiedEmail_returns403() throws Exception {
        doRegister("ivy@example.com");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "ivy@example.com", "password", "secret123"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "ghost@example.com", "password", "secret123"))))
            .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // JWT-gated endpoints
    // -------------------------------------------------------------------------

    @Test
    void adminEndpoint_noToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_buyerToken_returns403() throws Exception {
        String token = jwtUtil.generate("buyer@example.com", "BUYER");

        mvc.perform(get("/api/v1/admin/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_adminToken_returns200() throws Exception {
        String token = jwtUtil.generate("admin@example.com", "ADMIN");

        mvc.perform(get("/api/v1/admin/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void doRegister(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email, "password", "secret123"))))
            .andExpect(status().isCreated());
    }

    private void doRegisterAndVerify(String email) throws Exception {
        doRegister(email);
        String token = tokenRepo.findAll().stream()
            .filter(t -> t.getUser().getEmail().equals(email))
            .findFirst().orElseThrow().getToken();
        mvc.perform(get("/api/v1/auth/verify-email").param("token", token))
            .andExpect(status().isOk());
    }

    private User saveUnverifiedUser(String email) {
        var user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode("secret123"));
        user.setRole(UserRole.BUYER);
        return userRepo.save(user);
    }
}
