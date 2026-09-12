package iq.mizan.sync.mapper;

import iq.mizan.sync.dto.ConflictResponse;
import iq.mizan.sync.dto.DeviceResponse;
import iq.mizan.sync.entity.SyncConflict;
import iq.mizan.sync.entity.SyncDevice;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class SyncMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mapping(target = "table", source = "tableName")
    @Mapping(target = "clientValue", source = "clientValue", qualifiedByName = "jsonValue")
    @Mapping(target = "serverValue", source = "serverValue", qualifiedByName = "jsonValue")
    public abstract ConflictResponse toResponse(SyncConflict conflict);

    public abstract DeviceResponse toResponse(SyncDevice device);

    @Named("jsonValue")
    JsonNode jsonValue(String json) {
        return json == null ? null : JSON.readTree(json);
    }
}
