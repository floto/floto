package io.github.floto.core.virtualization.workstation;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Arrays;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class ShellExecutor {
    private Logger log = LoggerFactory.getLogger(ShellExecutor.class);

    private long timeoutInMs = 30000;

    public String execute(String command, String... arguments) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            final CommandLine cmdLine = new CommandLine(getActualExecutable(command));
            for(String argument: arguments) {
                if (System.getProperty("os.name").contains("Windows")) {
                    // Handle Windows CMD quoting shenanigans
                    argument = argument.replaceAll("\"","\"\"");
                    argument = "\""+argument+"\"";
                }
                cmdLine.addArgument(argument, false);
            }

            final DefaultExecutor executor = new DefaultExecutor();
            executor.setStreamHandler(new PumpStreamHandler(baos));
            executor.setWatchdog(new ExecuteWatchdog(timeoutInMs));

            log.info("execute:" + cmdLine.toString());
            executor.execute(cmdLine);

            String commandOutput;
            try {
                commandOutput = IOUtils.toString(baos.toByteArray(), "UTF-8");
            } catch (Throwable t) {
                throw Throwables.propagate(t);
            }
            return commandOutput;
        } catch (Throwable e) {
            String commandOutput;
            try {
                commandOutput = IOUtils.toString(baos.toByteArray(), "UTF-8");
            } catch (Throwable t) {
                commandOutput = "Error getting command output: " + t.getMessage();
            }
            throw new RuntimeException("Error running command " + command + " " + String.join(" ", Arrays.asList(arguments)) + "\n" + commandOutput, e);
        }

    }

    private String getActualExecutable(String command) {
        return command;
    }

    public void setTimeout(Duration timeout) {
        timeoutInMs = timeout.toMillis();
    }

    public static void main(String[] args) {
		new ShellExecutor().execute(args[0], Arrays.copyOfRange(args, 1, args.length));
	}

}
