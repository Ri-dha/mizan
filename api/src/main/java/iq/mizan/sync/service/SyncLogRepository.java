package iq.mizan.sync.service;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
public class SyncLogRepository {

    public record Entry(long seq, String tableName, UUID rowId) {
    }

    private final JdbcClient jdbcClient;

    public List<Entry> after(long seq, int limit) {
        return jdbcClient.sql("select seq, table_name, row_id from sync_log where seq > ? order by seq limit ?")
                .param(seq)
                .param(limit)
                .query((rs, i) -> new Entry(rs.getLong("seq"), rs.getString("table_name"), rs.getObject("row_id", UUID.class)))
                .list();
    }
}
