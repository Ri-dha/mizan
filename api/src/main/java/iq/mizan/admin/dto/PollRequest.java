package iq.mizan.admin.dto;

import jakarta.validation.constraints.NotNull;

/** DOLLAR spends one IQWealth request; ALL fetches every other feed and reads the dollar from cache. */
public record PollRequest(@NotNull Target target) {
    public enum Target { DOLLAR, ALL }
}
