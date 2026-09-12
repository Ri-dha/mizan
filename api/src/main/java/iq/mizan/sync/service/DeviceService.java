package iq.mizan.sync.service;

import java.util.UUID;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.sync.dto.DeviceResponse;
import iq.mizan.sync.entity.SyncDevice;
import iq.mizan.sync.mapper.SyncMapper;
import iq.mizan.sync.repository.SyncDeviceRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class DeviceService {

    private final SyncDeviceRepository deviceRepository;
    private final SyncMapper syncMapper;

    @Transactional
    public DeviceResponse register(CurrentUser user, String name) {
        return syncMapper.toResponse(deviceRepository.save(
                SyncDevice.register(user.householdId(), user.userId(), name)));
    }

    SyncDevice requireOwned(CurrentUser user, UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .filter(device -> device.belongsTo(user.userId()))
                .orElseThrow(() -> ApiException.of(
                        ErrorCode.SYNC_DEVICE_NOT_FOUND, "Register this device before syncing"));
    }
}
