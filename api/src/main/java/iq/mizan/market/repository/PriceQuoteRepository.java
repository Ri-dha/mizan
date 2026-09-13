package iq.mizan.market.repository;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceQuote;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceQuoteRepository extends JpaRepository<PriceQuote, Instrument> {
}
