package iq.mizan.auth.mapper;

import iq.mizan.auth.dto.CurrentUserResponse;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.household.mapper.HouseholdMapper;
import iq.mizan.household.service.HouseholdSummary;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = HouseholdMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "displayName", source = "user.displayName")
    @Mapping(target = "locale", source = "user.locale")
    @Mapping(target = "verified", source = "user.verified")
    @Mapping(target = "household", source = "household")
    CurrentUserResponse toResponse(AppUser user, HouseholdSummary household);
}
