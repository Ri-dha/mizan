package iq.mizan.vault.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.vault.entity.KeyKind;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Each household's current shared key versions (ADR e2e-4). */
@Repository
@AllArgsConstructor
public class KeyStateRepository {

    public record State(KeyKind kind, int currentVersion) {
    }

    private final JdbcClient jdbc;

    public List<State> all(UUID householdId) {
        return jdbc.sql("select key_kind, current_version from household_key_state where household_id = ? order by key_kind")
                .param(householdId)
                .query((rs, i) -> new State(KeyKind.valueOf(rs.getString("key_kind")), rs.getInt("current_version")))
                .list();
    }

    public Optional<Integer> current(UUID householdId, KeyKind kind) {
        return jdbc.sql("select current_version from household_key_state where household_id = ? and key_kind = ?")
                .param(householdId).param(kind.name()).query(Integer.class).optional();
    }

    /** Held until the rotating transaction ends, so two devices cannot both advance the version. */
    public Optional<Integer> lockCurrent(UUID householdId, KeyKind kind) {
        return jdbc.sql("select current_version from household_key_state where household_id = ? and key_kind = ? for update")
                .param(householdId).param(kind.name()).query(Integer.class).optional();
    }

    public void start(UUID householdId, KeyKind kind, int version) {
        jdbc.sql("insert into household_key_state (household_id, key_kind, current_version) values (?, ?, ?) on conflict do nothing")
                .param(householdId).param(kind.name()).param(version).update();
    }

    public void advance(UUID householdId, KeyKind kind, int version, Instant now) {
        jdbc.sql("update household_key_state set current_version = ?, rotated_at = ? where household_id = ? and key_kind = ?")
                .param(version).param(Timestamp.from(now)).param(householdId).param(kind.name()).update();
    }

    public void forget(UUID householdId) {
        jdbc.sql("delete from household_key_state where household_id = ?").param(householdId).update();
    }
}
