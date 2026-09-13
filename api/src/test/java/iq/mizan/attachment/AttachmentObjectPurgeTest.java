package iq.mizan.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import iq.mizan.attachment.job.AttachmentObjectPurge;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The job targets exactly the attachments whose rows are about to leave the recycle bin. */
class AttachmentObjectPurgeTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private AttachmentObjectPurge purge;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyAttachmentsPastTheRetentionWindowAreTargeted() {
        resetTransactionalData();
        api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");
        Map<String, Object> ids = jdbc.queryForMap("select u.id as user_id, m.household_id from app_user u join membership m on m.user_id = u.id");
        insert(ids, "BACKUP", "now() - interval '45 days'");
        insert(ids, "RECEIPT", "now() - interval '2 days'");
        insert(ids, "RECEIPT", null);

        assertThat(purge.purge()).isEqualTo(1);
    }

    private void insert(Map<String, Object> ids, String ownerType, String deletedAt) {
        jdbc.update("insert into attachment (id, household_id, owner_id, visibility, owner_type, owner_record_id, mime_type, byte_size, iv, deleted_at) "
                        + "values (?, ?, ?, 'PRIVATE', ?, ?, 'application/octet-stream', 10, 'aXY=', " + (deletedAt == null ? "null" : deletedAt) + ")",
                UUID.randomUUID(), ids.get("household_id"), ids.get("user_id"), ownerType, ids.get("user_id"));
    }
}
