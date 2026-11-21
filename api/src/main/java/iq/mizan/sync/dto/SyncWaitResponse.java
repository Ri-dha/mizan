package iq.mizan.sync.dto;

/** {@code changed}: the household's feed has moved past the device's sequence; pull now. */
public record SyncWaitResponse(boolean changed) {

    public static final SyncWaitResponse CHANGED = new SyncWaitResponse(true);
    public static final SyncWaitResponse UNCHANGED = new SyncWaitResponse(false);
}
