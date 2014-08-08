package io.github.floto.core.tasks;

public interface HostManipulator {
    void run(String command);
    void writeToVm(String content, String destination);
}
