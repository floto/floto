package io.github.floto.core.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerImageDescription {
	@SuppressWarnings("NM_FIELD_NAMING_CONVENTION")
    public String Id;
	@SuppressWarnings("NM_FIELD_NAMING_CONVENTION")
    public String ParentId;
	@SuppressWarnings("NM_FIELD_NAMING_CONVENTION")
    public List<String> RepoTags = new ArrayList<>();

    @Override
    public String toString() {
        return "DockerImageDescription{" +
                "Id='" + Id + '\'' +
                ", ParentId='" + ParentId + '\'' +
                ", RepoTags=" + RepoTags +
                '}';
    }
}
