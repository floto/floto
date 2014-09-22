package io.github.floto.core;

public class ContainerState {
    public enum Status {
        running,
        stopped
    }

    public String containerName;
    public Status status;
    public String hostName;
}
