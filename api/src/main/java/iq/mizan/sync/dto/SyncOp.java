package iq.mizan.sync.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** One client write: the fields it changed on a row, each with the clock of that change. */
public record SyncOp(
        @NotNull UUID opId,
        @NotBlank String table,
        @NotNull UUID rowId,
        @NotEmpty Map<String, Object> fields,
        @NotEmpty Map<String, String> clocks) {
}
