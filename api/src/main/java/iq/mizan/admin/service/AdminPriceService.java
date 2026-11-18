package iq.mizan.admin.service;

import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.market.dto.PriceChangeResponse;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.service.MarketService;
import iq.mizan.market.service.PriceHistoryService;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An operator's price fix and its audit entry commit together, or neither does. */
@Service
@AllArgsConstructor
public class AdminPriceService {

    private final MarketService marketService;
    private final PriceHistoryService priceHistory;
    private final AuditService auditService;

    @Transactional
    public void correct(Instrument instrument, long priceMicros, String reason, UUID actor) {
        marketService.correct(instrument, priceMicros);
        auditService.record(AuditRecord.of(AuditAction.PRICE_CORRECTED, AuditEntityType.PRICE).actorId(actor)
                .after(Map.of("instrument", instrument.name(), "priceMicros", priceMicros, "reason", reason)).build());
    }

    @Transactional
    public void delete(long id, String reason, UUID actor) {
        Instrument instrument = priceHistory.instrumentOf(id);
        PriceChangeResponse removed = priceHistory.deleteTick(id);
        auditService.record(AuditRecord.of(AuditAction.PRICE_TICK_DELETED, AuditEntityType.PRICE).actorId(actor)
                .before(Map.of("id", removed.id(), "instrument", String.valueOf(instrument), "priceMicros", removed.priceMicros(),
                        "observedAt", removed.observedAt().toString(), "source", removed.source()))
                .after(Map.of("reason", reason)).build());
    }
}
