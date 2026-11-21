package iq.mizan.auth.service;

import java.util.UUID;

import iq.mizan.auth.dto.CurrentUserResponse;
import iq.mizan.auth.dto.UpdateProfileRequest;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.household.service.CurrencyChoices;
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
    private final CurrencyChoices currencyChoices;

    @Transactional
    public CurrentUserResponse update(UUID userId, UpdateProfileRequest request) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.USER_NOT_FOUND, "This account no longer exists"));
        if (request.country() != null) {
            user.changeCountry(currencyChoices.country(request.country()));
        }
        if (request.trackedCurrencies() != null) {
            user.trackCurrencies(request.trackedCurrencies().stream().distinct().map(currencyChoices::currency).toList());
        }
        return userMapper.toResponse(user, householdService.summaryFor(userId));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse describe(UUID userId) {
        return userMapper.toResponse(
                userRepository.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.USER_NOT_FOUND, "This account no longer exists")),
                householdService.summaryFor(userId));
    }
}
