package iq.mizan.platform.repository;

import iq.mizan.platform.entity.PlatformSetting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
}
