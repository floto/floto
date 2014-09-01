package io.github.floto.core.virtualization.workstation;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

public class ExternalProgram {
    private static Logger log = LoggerFactory.getLogger(ShellExecutor.class);
    private String command;
    private ShellExecutor shellExecutor = new ShellExecutor();


    ExternalProgram(String command) {
        this.command = command;
    }

    public static ExternalProgram create(String name, String... potentialProgramDirectories) {
        String command = name;
        if (System.getProperty("os.name").contains("Windows")) {
            // probably not in bin, try to find it
            command += ".exe";
            outer: for(String env: Arrays.asList("PROGRAMFILES", "PROGRAMFILES(X86)")) {
                String directory = System.getenv(env);
                if(directory == null) {
                    continue;
                }
                for(String subDirectory: potentialProgramDirectories) {
                    File subDirectoryFile = new File(directory + "\\" + subDirectory);
                    if(!subDirectoryFile.isDirectory()) {
                        continue;
                    }
                    Collection<File> candidates = FileUtils.listFiles(subDirectoryFile, FileFilterUtils.nameFileFilter(command, IOCase.INSENSITIVE), FileFilterUtils.trueFileFilter());
                    if(!candidates.isEmpty()) {
                        command = candidates.iterator().next().getAbsolutePath();
                        break outer;
                    }
                }
            }
            log.info("Mapped {} to {}", name, command);
        }

        return new ExternalProgram(command);
    }

    public String run(String... args) {
        return shellExecutor.execute(command, args);
    }

    public void setTimeout(Duration timeout) {
        shellExecutor.setTimeout(timeout);
    }


}
