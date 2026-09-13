package iq.mizan.household.mapper;

import iq.mizan.household.dto.HouseholdResponse;
import iq.mizan.household.entity.Household;
import iq.mizan.household.entity.Membership;
import iq.mizan.household.service.HouseholdSummary;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HouseholdMapper {

    @Mapping(target = "id", source = "household.id")
    @Mapping(target = "name", source = "household.name")
    @Mapping(target = "baseCurrency", source = "household.baseCurrency")
    @Mapping(target = "monthStartDay", source = "household.monthStartDay")
    @Mapping(target = "role", source = "membership.role")
    @Mapping(target = "permissions", expression = "java(membership.getRole().permissions())")
    @Mapping(target = "deletionRequestedAt", source = "household.deletionRequestedAt")
    HouseholdSummary toSummary(Household household, Membership membership);

    HouseholdResponse toResponse(HouseholdSummary summary);
}
