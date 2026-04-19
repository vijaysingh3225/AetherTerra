package com.aetherterra.auctions;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.auth.JwtUtil;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuctionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired AuctionRepository auctionRepo;
    @Autowired BidRepository bidRepo;
    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean JavaMailSender mailSender;

    private User admin;
    private User bidder;

    @BeforeEach
    void setup() {
        bidRepo.deleteAll();
        auctionRepo.deleteAll();
        admin = userRepo.findByEmail("admin@aetherterra.com").orElseGet(() -> {
            var u = new User();
            u.setEmail("admin@aetherterra.com");
            u.setPasswordHash(encoder.encode("admin123"));
            u.setRole(UserRole.ADMIN);
            u.setEmailVerifiedAt(Instant.now());
            return userRepo.save(u);
        });
        bidder = userRepo.findByEmail("bidder@example.com").orElseGet(() -> {
            var u = new User();
            u.setEmail("bidder@example.com");
            u.setPasswordHash(encoder.encode("secret123"));
            u.setRole(UserRole.BUYER);
            u.setEmailVerifiedAt(Instant.now());
            u.setShirtSize("L");
            return userRepo.save(u);
        });
    }

    @AfterEach
    void cleanup() {
        bidRepo.deleteAll();
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
            .andExpect(jsonPath("$.data.startingBid").value(100.00))
            .andExpect(jsonPath("$.data.bidCount").value(0));
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

    @Test
    void getBids_returnsBidHistoryNewestFirst() throws Exception {
        Auction auction = saveAuction("terra-one", AuctionStatus.LIVE);
        saveBid(auction, bidder, new BigDecimal("125.00"), Instant.now().minus(2, ChronoUnit.HOURS));
        saveBid(auction, bidder, new BigDecimal("140.00"), Instant.now().minus(30, ChronoUnit.MINUTES));
        auction.setCurrentBid(new BigDecimal("140.00"));
        auctionRepo.save(auction);

        mvc.perform(get("/api/v1/auctions/terra-one/bids"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].amount").value(140.00))
            .andExpect(jsonPath("$.data[1].amount").value(125.00));
    }

    @Test
    void getBids_unknownAuction_returns404() throws Exception {
        mvc.perform(get("/api/v1/auctions/missing-auction/bids"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getBids_noAuthRequired() throws Exception {
        Auction auction = saveAuction("terra-one", AuctionStatus.LIVE);
        saveBid(auction, bidder, new BigDecimal("125.00"), Instant.now().minus(30, ChronoUnit.MINUTES));

        mvc.perform(get("/api/v1/auctions/terra-one/bids"))
            .andExpect(status().isOk());
    }

    @Test
    void placeBid_liveAuction_createsBidAndUpdatesCurrentBid() throws Exception {
        Auction auction = saveAuction("terra-one", AuctionStatus.LIVE);
        String token = jwtUtil.generate(bidder.getEmail(), bidder.getRole().name());

        mvc.perform(post("/api/v1/auctions/terra-one/bids")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("amount", 125.00))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.amount").value(125.00));

        Auction updated = auctionRepo.findById(auction.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getCurrentBid()).isEqualByComparingTo("125.00");
        org.assertj.core.api.Assertions.assertThat(bidRepo.countByAuctionId(auction.getId())).isEqualTo(1);
    }

    @Test
    void placeBid_unknownAuction_returns404() throws Exception {
        String token = jwtUtil.generate(bidder.getEmail(), bidder.getRole().name());

        mvc.perform(post("/api/v1/auctions/missing-auction/bids")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("amount", 125.00))))
            .andExpect(status().isNotFound());
    }

    @Test
    void placeBid_guestReturns401() throws Exception {
        saveAuction("terra-one", AuctionStatus.LIVE);

        mvc.perform(post("/api/v1/auctions/terra-one/bids")
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("amount", 125.00))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void placeBid_mustBeGreaterThanCurrentBid() throws Exception {
        Auction auction = saveAuction("terra-one", AuctionStatus.LIVE);
        auction.setCurrentBid(new BigDecimal("130.00"));
        auctionRepo.save(auction);
        String token = jwtUtil.generate(bidder.getEmail(), bidder.getRole().name());

        mvc.perform(post("/api/v1/auctions/terra-one/bids")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("amount", 130.00))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void placeBid_scheduledAuctionReturns400() throws Exception {
        saveAuction("terra-one", AuctionStatus.SCHEDULED);
        String token = jwtUtil.generate(bidder.getEmail(), bidder.getRole().name());

        mvc.perform(post("/api/v1/auctions/terra-one/bids")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("amount", 125.00))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void placeBid_endedAuctionReturns400() throws Exception {
        saveAuction("terra-one", AuctionStatus.ENDED);
        String token = jwtUtil.generate(bidder.getEmail(), bidder.getRole().name());

        mvc.perform(post("/api/v1/auctions/terra-one/bids")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(om.writeValueAsString(Map.of("amount", 125.00))))
            .andExpect(status().isBadRequest());
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

    private Bid saveBid(Auction auction, User user, BigDecimal amount, Instant placedAt) {
        var bid = new Bid();
        bid.setAuctionId(auction.getId());
        bid.setBidderId(user.getId());
        bid.setAmount(amount);
        bid.setPlacedAt(placedAt);
        return bidRepo.save(bid);
    }
}
