package io.github.floto.core.jobs;

public interface HostManipulator {
    void run(String command);
    void writeToVm(String content, String destination);
}
