package io.github.floto.dsl.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = WorkstationHypervisorDescription.class, name = "workstation"),
        @JsonSubTypes.Type(value = EsxHypervisorDescription.class, name = "esx")})
public abstract class HypervisorDescription {
}
