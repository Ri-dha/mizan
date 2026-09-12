package iq.mizan.attachment.sync;

import java.util.Map;

import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;

import org.springframework.stereotype.Component;

@Component
public class AttachmentSyncTable implements SyncTable {

    public static final String NAME = "attachment";

    private static final Map<String, SyncColumnType> FIELDS = Map.ofEntries(
            Map.entry("visibility", SyncColumnType.TEXT),
            Map.entry("ownerType", SyncColumnType.TEXT),
            Map.entry("ownerRecordId", SyncColumnType.UUID_VALUE),
            Map.entry("mimeType", SyncColumnType.TEXT),
            Map.entry("byteSize", SyncColumnType.BIGINT),
            Map.entry("iv", SyncColumnType.TEXT),
            Map.entry("uploadedAt", SyncColumnType.TIMESTAMP),
            Map.entry("deletedAt", SyncColumnType.TIMESTAMP));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, SyncColumnType> fields() {
        return FIELDS;
    }
}
