package iq.mizan.market.repository;

import iq.mizan.market.entity.MarketCatalogue;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCatalogueRepository extends JpaRepository<MarketCatalogue, String> {
}
