package iq.mizan.market.mapper;

import iq.mizan.market.dto.HistoryPointResponse;
import iq.mizan.market.dto.QuoteResponse;
import iq.mizan.market.entity.PriceHistory;
import iq.mizan.market.entity.PriceQuote;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MarketMapper {

    @Mapping(target = "stale", source = "stale")
    QuoteResponse toResponse(PriceQuote quote, boolean stale);

    @Mapping(target = "day", source = "key.day")
    HistoryPointResponse toResponse(PriceHistory history);
}
