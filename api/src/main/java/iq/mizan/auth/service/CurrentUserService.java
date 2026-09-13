package iq.mizan.auth.service;

import java.util.UUID;

import iq.mizan.auth.dto.CurrentUserResponse;
import iq.mizan.auth.mapper.UserMapper;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.household.service.HouseholdService;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class CurrentUserService {

    private final AppUserRepository userRepository;
    private final HouseholdService householdService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public CurrentUserResponse describe(UUID userId) {
        return userMapper.toResponse(
                userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.USER_NOT_FOUND, "This account no longer exists")),
                householdService.summaryFor(userId));
    }
}
