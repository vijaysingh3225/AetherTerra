package com.aetherterra.auctions;

import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@Profile("local")
@Order(2)
public class LocalAuctionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAuctionSeeder.class);

    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public LocalAuctionSeeder(
            AuctionRepository auctionRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        var admin = userRepository.findByEmail("admin@aetherterra.com").orElse(null);
        if (admin == null) {
            return;
        }

        removeLegacySamples(List.of("terra-one", "void-series-01"));

        Instant now = Instant.now();
        User bidderOne = ensureBuyer("maya@example.com", "M");
        User bidderTwo = ensureBuyer("leo@example.com", "L");
        User bidderThree = ensureBuyer("sora@example.com", "XL");

        seedAuction(admin, new SampleAuction(
                "terra-signal-001",
                "Terra Signal 001 - Ink Fracture Tee",
                "Oversized heavyweight tee with cracked front print, faded mineral wash, and a one-off hand-finished hem.",
                AuctionStatus.LIVE,
                new BigDecimal("85.00"),
                now.minus(6, ChronoUnit.HOURS),
                now.plus(18, ChronoUnit.HOURS),
                List.of(
                        new SampleBid(bidderOne.getId(), new BigDecimal("92.00"), now.minus(5, ChronoUnit.HOURS)),
                        new SampleBid(bidderTwo.getId(), new BigDecimal("108.00"), now.minus(3, ChronoUnit.HOURS)),
                        new SampleBid(bidderThree.getId(), new BigDecimal("126.00"), now.minus(45, ChronoUnit.MINUTES))
                )
        ));

        seedAuction(admin, new SampleAuction(
                "ether-bloom-002",
                "Ether Bloom 002 - Sunfade Graphic",
                "Boxy cream blank with a soft-touch chest print and pigment fade. Cut and sewn only after the winner is locked.",
                AuctionStatus.LIVE,
                new BigDecimal("95.00"),
                now.minus(2, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.DAYS),
                List.of(
                        new SampleBid(bidderTwo.getId(), new BigDecimal("110.00"), now.minus(90, ChronoUnit.MINUTES)),
                        new SampleBid(bidderOne.getId(), new BigDecimal("135.00"), now.minus(20, ChronoUnit.MINUTES))
                )
        ));

        seedAuction(admin, new SampleAuction(
                "nocturne-grid-003",
                "Nocturne Grid 003 - Split Dye Edition",
                "Charcoal cotton jersey with uneven hand dye, tonal back mark, and a relaxed shoulder line.",
                AuctionStatus.LIVE,
                new BigDecimal("70.00"),
                now.minus(30, ChronoUnit.MINUTES),
                now.plus(3, ChronoUnit.DAYS),
                List.of()
        ));

        seedAuction(admin, new SampleAuction(
                "void-series-004",
                "Void Series 004 - Garment Dye Drop",
                "Dense jersey, overlocked seams, and a washed black finish. Scheduled next to preview the upcoming state.",
                AuctionStatus.SCHEDULED,
                new BigDecimal("120.00"),
                now.plus(20, ChronoUnit.HOURS),
                now.plus(5, ChronoUnit.DAYS),
                List.of()
        ));

        seedAuction(admin, new SampleAuction(
                "afterglow-archive-005",
                "Afterglow Archive 005 - Finished Auction",
                "Previously closed one-of-one tee with a soft gradient print and single-stitch neck detail.",
                AuctionStatus.ENDED,
                new BigDecimal("80.00"),
                now.minus(5, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.DAYS),
                List.of(
                        new SampleBid(bidderThree.getId(), new BigDecimal("100.00"), now.minus(4, ChronoUnit.DAYS)),
                        new SampleBid(bidderOne.getId(), new BigDecimal("140.00"), now.minus(3, ChronoUnit.DAYS)),
                        new SampleBid(bidderTwo.getId(), new BigDecimal("165.00"), now.minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                )
        ));

        log.info("Local sample auctions refreshed for development.");
    }

    private void removeLegacySamples(List<String> legacySlugs) {
        legacySlugs.forEach(slug -> auctionRepository.findBySlug(slug).ifPresent(auction -> {
            jdbcTemplate.update("DELETE FROM bids WHERE auction_id = ?", auction.getId());
            auctionRepository.delete(auction);
        }));
    }

    private User ensureBuyer(String email, String shirtSize) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            var user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode("secret123"));
            user.setRole(UserRole.BUYER);
            user.setShirtSize(shirtSize);
            user.setEmailVerifiedAt(Instant.now());
            return userRepository.save(user);
        });
    }

    private void seedAuction(User admin, SampleAuction sample) {
        var auction = auctionRepository.findBySlug(sample.slug())
                .orElseGet(Auction::new);

        auction.setSlug(sample.slug());
        auction.setTitle(sample.title());
        auction.setDescription(sample.description());
        auction.setStatus(sample.status());
        auction.setStartingBid(sample.startingBid());
        auction.setCurrentBid(sample.bids().isEmpty()
                ? null
                : sample.bids().get(sample.bids().size() - 1).amount());
        auction.setStartsAt(sample.startsAt());
        auction.setEndsAt(sample.endsAt());
        auction.setCreatedById(admin.getId());

        if (sample.status() == AuctionStatus.ENDED && !sample.bids().isEmpty()) {
            auction.setWinnerId(sample.bids().get(sample.bids().size() - 1).bidderId());
        } else {
            auction.setWinnerId(null);
        }

        var saved = auctionRepository.save(auction);
        reseedBids(saved.getId(), sample.bids());
    }

    private void reseedBids(UUID auctionId, List<SampleBid> bids) {
        jdbcTemplate.update("DELETE FROM bids WHERE auction_id = ?", auctionId);
        for (SampleBid bid : bids) {
            jdbcTemplate.update(
                    "INSERT INTO bids (auction_id, bidder_id, amount, placed_at) VALUES (?, ?, ?, ?)",
                    auctionId,
                    bid.bidderId(),
                    bid.amount(),
                    Timestamp.from(bid.placedAt())
            );
        }
    }

    private record SampleAuction(
            String slug,
            String title,
            String description,
            AuctionStatus status,
            BigDecimal startingBid,
            Instant startsAt,
            Instant endsAt,
            List<SampleBid> bids
    ) {}

    private record SampleBid(
            UUID bidderId,
            BigDecimal amount,
            Instant placedAt
    ) {}
}
