package io.github.floto.core.tasks;

import com.google.common.base.Throwables;
import io.github.floto.core.ssh.SshService;
import io.github.floto.core.virtualization.HypervisorService;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class SshHostManipulator implements HostManipulator {

    private final SshService sshService;
    private String host;

    public SshHostManipulator(String host) {
        this.host = host;
        sshService = new SshService();
    }

    @Override
    public void run(String command) {
        // http://stackoverflow.com/questions/1250079/escaping-single-quotes-within-single-quoted-strings
        String quotedCommand = command.replaceAll("'", "'\"'\"'");
        String sudoCommand = "sudo bash -c '" + quotedCommand + "'";
        sshService.execute(host, sudoCommand);
    }

    @Override
    public void writeToVm(String content, String destination) {
        sshService.scp(host, content, "/tmp/tmpfile");
        int lastSlash = destination.lastIndexOf("/");
        if(lastSlash > 0) {
            String parentDirectory = destination.substring(0, lastSlash);
            sshService.execute(host, "sudo mkdir -p " + parentDirectory);
        }
        sshService.execute(host, "sudo mv /tmp/tmpfile " + destination);
    }
}
