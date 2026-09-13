package iq.mizan.market.repository;

import java.time.LocalDate;
import java.util.List;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceHistory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, PriceHistory.Key> {

    List<PriceHistory> findByKeyInstrumentAndKeyDayBetweenOrderByKeyDay(Instrument instrument, LocalDate from, LocalDate to);
}
