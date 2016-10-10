package io.github.floto.core.jobs;

import java.io.File;

public interface HostManipulator {
    void run(String command);
	void run(String command, int timeout);
    void writeToVm(String content, String destination);
    void copyToVm(File sourceFile, String destination);
}
