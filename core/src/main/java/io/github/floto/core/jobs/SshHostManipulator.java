package io.github.floto.core.jobs;

import java.io.File;

import io.github.floto.core.ssh.SshService;

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

	@Override
	public void copyToVm(File sourceFile, String destination) {
        sshService.scp(host, sourceFile, "/tmp/tmpfile");
        int lastSlash = destination.lastIndexOf("/");
        if(lastSlash > 0) {
            String parentDirectory = destination.substring(0, lastSlash);
            sshService.execute(host, "sudo mkdir -p " + parentDirectory);
        }
        sshService.execute(host, "sudo mv /tmp/tmpfile " + destination);
		
	}

}
