package com.aetherterra.auctions;

import com.aetherterra.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("local")
@Order(2)
public class LocalAuctionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalAuctionSeeder.class);

    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;

    public LocalAuctionSeeder(AuctionRepository auctionRepository, UserRepository userRepository) {
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (auctionRepository.count() > 0) return;

        var admin = userRepository.findByEmail("admin@aetherterra.com").orElse(null);
        if (admin == null) return;

        Instant now = Instant.now();

        var a1 = new Auction();
        a1.setSlug("terra-one");
        a1.setTitle("Terra One — Hand-printed Oversized Tee");
        a1.setDescription("100% heavyweight cotton, water-based inks hand-applied in-studio. Single run, no restock. Ships after auction close.");
        a1.setStatus(AuctionStatus.LIVE);
        a1.setStartingBid(new BigDecimal("75.00"));
        a1.setStartsAt(now.minus(1, ChronoUnit.HOURS));
        a1.setEndsAt(now.plus(2, ChronoUnit.DAYS));
        a1.setCreatedById(admin.getId());

        var a2 = new Auction();
        a2.setSlug("void-series-01");
        a2.setTitle("Void Series 01 — Garment Dyed Drop");
        a2.setDescription("Enzyme-washed, garment dyed in micro batches. Oversized silhouette with dropped shoulders. One made after winner confirmed.");
        a2.setStatus(AuctionStatus.SCHEDULED);
        a2.setStartingBid(new BigDecimal("120.00"));
        a2.setStartsAt(now.plus(3, ChronoUnit.DAYS));
        a2.setEndsAt(now.plus(8, ChronoUnit.DAYS));
        a2.setCreatedById(admin.getId());

        auctionRepository.saveAll(List.of(a1, a2));
        log.info("Seeded 2 sample auctions for local development.");
    }
}
