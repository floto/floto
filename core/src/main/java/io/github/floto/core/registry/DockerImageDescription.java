package io.github.floto.core.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerImageDescription {
    public String Id;
    public String ParentId;
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
