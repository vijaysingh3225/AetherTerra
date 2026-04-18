package com.aetherterra.auctions;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuctionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AuctionRepository auctionRepo;
    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mailSender;

    private User admin;

    @BeforeEach
    void setup() {
        auctionRepo.deleteAll();
        admin = userRepo.findByEmail("admin@aetherterra.com").orElseGet(() -> {
            var u = new User();
            u.setEmail("admin@aetherterra.com");
            u.setPasswordHash(encoder.encode("admin123"));
            u.setRole(UserRole.ADMIN);
            u.setEmailVerifiedAt(Instant.now());
            return userRepo.save(u);
        });
    }

    @AfterEach
    void cleanup() {
        auctionRepo.deleteAll();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auctions
    // -------------------------------------------------------------------------

    @Test
    void listAuctions_empty_returnsEmptyList() throws Exception {
        mvc.perform(get("/api/v1/auctions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listAuctions_noAuthRequired() throws Exception {
        mvc.perform(get("/api/v1/auctions"))
            .andExpect(status().isOk());
    }

    @Test
    void listAuctions_returnsLiveAndScheduledButNotCancelled() throws Exception {
        saveAuction("live-one", AuctionStatus.LIVE);
        saveAuction("scheduled-one", AuctionStatus.SCHEDULED);
        saveAuction("ended-one", AuctionStatus.ENDED);
        saveAuction("cancelled-one", AuctionStatus.CANCELLED);

        mvc.perform(get("/api/v1/auctions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3)); // LIVE + SCHEDULED + ENDED
    }

    @Test
    void listAuctions_orderedByEndsAtAscending() throws Exception {
        var near = saveAuction("ends-soon", AuctionStatus.LIVE);
        near.setEndsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        auctionRepo.save(near);

        var far = saveAuction("ends-later", AuctionStatus.LIVE);
        far.setEndsAt(Instant.now().plus(5, ChronoUnit.DAYS));
        auctionRepo.save(far);

        mvc.perform(get("/api/v1/auctions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].slug").value("ends-soon"))
            .andExpect(jsonPath("$.data[1].slug").value("ends-later"));
    }

    @Test
    void listAuctions_includesStartingBidAndDescription() throws Exception {
        saveAuction("terra-one", AuctionStatus.LIVE);

        mvc.perform(get("/api/v1/auctions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].startingBid").value(100.00))
            .andExpect(jsonPath("$.data[0].description").value("Test description"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auctions/{slug}
    // -------------------------------------------------------------------------

    @Test
    void getAuction_bySlug_returnsAuction() throws Exception {
        saveAuction("terra-one", AuctionStatus.LIVE);

        mvc.perform(get("/api/v1/auctions/terra-one"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.slug").value("terra-one"))
            .andExpect(jsonPath("$.data.status").value("LIVE"))
            .andExpect(jsonPath("$.data.startingBid").value(100.00));
    }

    @Test
    void getAuction_unknownSlug_returns404() throws Exception {
        mvc.perform(get("/api/v1/auctions/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getAuction_noAuthRequired() throws Exception {
        saveAuction("terra-one", AuctionStatus.LIVE);

        mvc.perform(get("/api/v1/auctions/terra-one"))
            .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Auction saveAuction(String slug, AuctionStatus status) {
        var a = new Auction();
        a.setSlug(slug);
        a.setTitle("Test Auction — " + slug);
        a.setDescription("Test description");
        a.setStatus(status);
        a.setStartingBid(new BigDecimal("100.00"));
        a.setStartsAt(Instant.now().minus(1, ChronoUnit.HOURS));
        a.setEndsAt(Instant.now().plus(2, ChronoUnit.DAYS));
        a.setCreatedById(admin.getId());
        return auctionRepo.save(a);
    }
}
