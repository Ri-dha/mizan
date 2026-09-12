package iq.mizan.sync.dto;

import java.util.List;

public record SyncPullResponse(List<SyncRecordResponse> records, long nextSeq, boolean hasMore) {
}
