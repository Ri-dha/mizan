package iq.mizan.market.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceTick;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

    Optional<PriceTick> findFirstByInstrumentOrderByObservedAtDesc(Instrument instrument);

    Optional<PriceTick> findFirstByInstrumentAndObservedAtLessThanOrderByObservedAtDesc(Instrument instrument, Instant before);

    boolean existsByInstrumentAndObservedAt(Instrument instrument, Instant observedAt);

    List<PriceTick> findByInstrumentAndObservedAtGreaterThanEqualAndObservedAtLessThanOrderByObservedAtDesc(
            Instrument instrument, Instant from, Instant before, Limit limit);
}
