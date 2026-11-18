package iq.mizan.platform.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "platform_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformSetting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "text_value")
    private String textValue;

    @Column(name = "secret_value")
    private byte[] secretValue;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PlatformSetting of(String key) {
        PlatformSetting setting = new PlatformSetting();
        setting.key = key;
        return setting;
    }

    public void setText(String value, UUID by, Instant at) {
        textValue = value;
        secretValue = null;
        stamp(by, at);
    }

    public void setSecret(byte[] sealed, UUID by, Instant at) {
        secretValue = sealed;
        textValue = null;
        stamp(by, at);
    }

    private void stamp(UUID by, Instant at) {
        updatedBy = by;
        updatedAt = at;
    }
}
