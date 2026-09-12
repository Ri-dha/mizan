package iq.mizan.sync.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SyncPushRequest(@NotNull UUID deviceId, @NotNull @Valid List<SyncOp> ops) {
}
