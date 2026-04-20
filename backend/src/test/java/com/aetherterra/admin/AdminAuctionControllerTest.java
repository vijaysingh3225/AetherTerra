package com.aetherterra.admin;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.auth.JwtUtil;
import com.aetherterra.auctions.Auction;
import com.aetherterra.auctions.AuctionRepository;
import com.aetherterra.auctions.AuctionStatus;
import com.aetherterra.bids.Bid;
import com.aetherterra.bids.BidRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuctionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AuctionRepository auctionRepo;
    @Autowired BidRepository bidRepo;
    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean JavaMailSender mailSender;

    private User admin;
    private User buyer;
    private String adminToken;
    private String buyerToken;

    @BeforeEach
    void setup() {
        bidRepo.deleteAll();
        auctionRepo.deleteAll();

        admin = userRepo.findByEmail("admin@aetherterra.com").orElseGet(User::new);
        admin.setEmail("admin@aetherterra.com");
        admin.setPasswordHash(encoder.encode("admin123"));
        admin.setRole(UserRole.ADMIN);
        admin.setEmailVerifiedAt(Instant.now());
        admin = userRepo.save(admin);

        buyer = userRepo.findByEmail("buyer@example.com").orElseGet(User::new);
        buyer.setEmail("buyer@example.com");
        buyer.setPasswordHash(encoder.encode("secret123"));
        buyer.setRole(UserRole.BUYER);
        buyer.setEmailVerifiedAt(Instant.now());
        buyer = userRepo.save(buyer);

        adminToken = jwtUtil.generate(admin.getEmail(), admin.getRole().name());
        buyerToken = jwtUtil.generate(buyer.getEmail(), buyer.getRole().name());
    }

    @AfterEach
    void cleanup() {
        bidRepo.deleteAll();
        auctionRepo.deleteAll();
    }

    // ── GET /api/v1/admin/auctions ────────────────────────────────────────────

    @Test
    void listAuctions_adminSeesAll() throws Exception {
        saveAuction("live-one", AuctionStatus.LIVE);
        saveAuction("sched-one", AuctionStatus.SCHEDULED);
        saveAuction("ended-one", AuctionStatus.ENDED);
        saveAuction("cancelled-one", AuctionStatus.CANCELLED);

        mvc.perform(get("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    void listAuctions_filterByStatus() throws Exception {
        saveAuction("live-one", AuctionStatus.LIVE);
        saveAuction("sched-one", AuctionStatus.SCHEDULED);

        mvc.perform(get("/api/v1/admin/auctions?status=LIVE")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].slug").value("live-one"));
    }

    @Test
    void listAuctions_filterBySearch() throws Exception {
        saveAuction("special-item", AuctionStatus.SCHEDULED);
        saveAuction("other-thing", AuctionStatus.SCHEDULED);

        mvc.perform(get("/api/v1/admin/auctions?search=special")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].slug").value("special-item"));
    }

    @Test
    void listAuctions_includesBidCount() throws Exception {
        Auction a = saveAuction("bid-test", AuctionStatus.LIVE);
        saveBid(a, buyer, new BigDecimal("110.00"), Instant.now().minus(1, ChronoUnit.HOURS));
        saveBid(a, buyer, new BigDecimal("120.00"), Instant.now().minus(30, ChronoUnit.MINUTES));

        mvc.perform(get("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].bidCount").value(2));
    }

    @Test
    void listAuctions_guestReturns401() throws Exception {
        mvc.perform(get("/api/v1/admin/auctions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listAuctions_buyerReturns403() throws Exception {
        mvc.perform(get("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isForbidden());
    }

    // ── POST /api/v1/admin/auctions ───────────────────────────────────────────

    @Test
    void createAuction_success() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(3, ChronoUnit.DAYS);

        mvc.perform(post("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "title", "Test Sneaker Drop",
                        "description", "Limited edition",
                        "startingBid", 50.00,
                        "startsAt", start.toString(),
                        "endsAt", end.toString()
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("Test Sneaker Drop"))
            .andExpect(jsonPath("$.data.slug").value("test-sneaker-drop"))
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.startingBid").value(50.00));

        assertThat(auctionRepo.findBySlug("test-sneaker-drop")).isPresent();
    }

    @Test
    void createAuction_generatesUniqueSlugOnConflict() throws Exception {
        saveAuction("test-sneaker-drop", AuctionStatus.SCHEDULED);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(3, ChronoUnit.DAYS);

        mvc.perform(post("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "title", "Test Sneaker Drop",
                        "description", "Another one",
                        "startingBid", 75.00,
                        "startsAt", start.toString(),
                        "endsAt", end.toString()
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.slug").value("test-sneaker-drop-1"));
    }

    @Test
    void createAuction_endsAtBeforeStartsAtReturns400() throws Exception {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);

        mvc.perform(post("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "title", "Bad Times",
                        "startingBid", 50.00,
                        "startsAt", start.toString(),
                        "endsAt", end.toString()
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createAuction_missingTitleReturns400() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(3, ChronoUnit.DAYS);

        mvc.perform(post("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "startingBid", 50.00,
                        "startsAt", start.toString(),
                        "endsAt", end.toString()
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createAuction_guestReturns401() throws Exception {
        mvc.perform(post("/api/v1/admin/auctions")
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "title", "Test", "startingBid", 50.00,
                        "startsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                        "endsAt", Instant.now().plus(3, ChronoUnit.DAYS).toString()
                ))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createAuction_buyerReturns403() throws Exception {
        mvc.perform(post("/api/v1/admin/auctions")
                .header("Authorization", "Bearer " + buyerToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "title", "Test", "startingBid", 50.00,
                        "startsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                        "endsAt", Instant.now().plus(3, ChronoUnit.DAYS).toString()
                ))))
            .andExpect(status().isForbidden());
    }

    // ── PATCH /api/v1/admin/auctions/{id} ─────────────────────────────────────

    @Test
    void updateAuction_scheduledFullEdit() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);
        Instant newStart = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant newEnd = Instant.now().plus(4, ChronoUnit.DAYS);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of(
                        "title", "Updated Title",
                        "startingBid", 200.00,
                        "startsAt", newStart.toString(),
                        "endsAt", newEnd.toString()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Updated Title"))
            .andExpect(jsonPath("$.data.startingBid").value(200.00));
    }

    @Test
    void updateAuction_scheduledToLive() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("status", "LIVE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("LIVE"));
    }

    @Test
    void updateAuction_scheduledToCancelled() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("status", "CANCELLED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void updateAuction_scheduledToEndedReturns422() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("status", "ENDED"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateAuction_liveToCancelled() throws Exception {
        Auction a = saveAuction("live-one", AuctionStatus.LIVE);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("status", "CANCELLED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void updateAuction_liveWithTitleReturns422() throws Exception {
        Auction a = saveAuction("live-one", AuctionStatus.LIVE);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("title", "New Title"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateAuction_endedReturns422() throws Exception {
        Auction a = saveAuction("ended-one", AuctionStatus.ENDED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("title", "Anything"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateAuction_cancelledReturns422() throws Exception {
        Auction a = saveAuction("cancelled-one", AuctionStatus.CANCELLED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("title", "Anything"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateAuction_invalidTimesReturns400() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);
        Instant badEnd = a.getStartsAt().minus(1, ChronoUnit.HOURS);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("endsAt", badEnd.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateAuction_unknownReturns404() throws Exception {
        mvc.perform(patch("/api/v1/admin/auctions/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("title", "X"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateAuction_guestReturns401() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("title", "X"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAuction_buyerReturns403() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(patch("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + buyerToken)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("title", "X"))))
            .andExpect(status().isForbidden());
    }

    // ── DELETE /api/v1/admin/auctions/{id} ────────────────────────────────────

    @Test
    void deleteAuction_scheduledNoBids_succeeds() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        assertThat(auctionRepo.findById(a.getId())).isEmpty();
    }

    @Test
    void deleteAuction_scheduledWithBids_returns422() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);
        saveBid(a, buyer, new BigDecimal("110.00"), Instant.now().minus(1, ChronoUnit.HOURS));

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteAuction_liveReturns422() throws Exception {
        Auction a = saveAuction("live-one", AuctionStatus.LIVE);

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteAuction_endedReturns422() throws Exception {
        Auction a = saveAuction("ended-one", AuctionStatus.ENDED);

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteAuction_cancelledReturns422() throws Exception {
        Auction a = saveAuction("cancelled-one", AuctionStatus.CANCELLED);

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteAuction_unknownReturns404() throws Exception {
        mvc.perform(delete("/api/v1/admin/auctions/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteAuction_guestReturns401() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAuction_buyerReturns403() throws Exception {
        Auction a = saveAuction("scheduled-one", AuctionStatus.SCHEDULED);

        mvc.perform(delete("/api/v1/admin/auctions/" + a.getId())
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/admin/dashboard ───────────────────────────────────────────

    @Test
    void dashboard_countsLiveAuctionsOnly() throws Exception {
        saveAuction("live-one", AuctionStatus.LIVE);
        saveAuction("live-two", AuctionStatus.LIVE);
        saveAuction("sched-one", AuctionStatus.SCHEDULED);

        mvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.liveAuctions").value(2));
    }

    @Test
    void dashboard_countsTotalBids() throws Exception {
        Auction a1 = saveAuction("live-one", AuctionStatus.LIVE);
        Auction a2 = saveAuction("live-two", AuctionStatus.LIVE);
        saveBid(a1, buyer, new BigDecimal("110.00"), Instant.now().minus(2, ChronoUnit.HOURS));
        saveBid(a2, buyer, new BigDecimal("120.00"), Instant.now().minus(1, ChronoUnit.HOURS));
        saveBid(a2, buyer, new BigDecimal("130.00"), Instant.now().minus(30, ChronoUnit.MINUTES));

        mvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalBids").value(3));
    }

    @Test
    void dashboard_guestReturns401() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard"))
            .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private Bid saveBid(Auction auction, User user, BigDecimal amount, Instant placedAt) {
        var bid = new Bid();
        bid.setAuctionId(auction.getId());
        bid.setBidderId(user.getId());
        bid.setAmount(amount);
        bid.setPlacedAt(placedAt);
        return bidRepo.save(bid);
    }
}
