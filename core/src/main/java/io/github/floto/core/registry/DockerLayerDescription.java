package io.github.floto.core.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerLayerDescription {
    public String id;
    public String parent;

    @Override
    public String toString() {
        return "DockerLayerDescription{" +
                "id='" + id + '\'' +
                ", parent='" + parent + '\'' +
                '}';
    }
}
