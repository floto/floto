package io.github.floto.builder;

import io.github.floto.core.ssh.SshService;

public class SshSandbox {

    public static void main(String[] args) {
        SshService sshService = new SshService();
        sshService.scp("192.168.95.137", "foo\n\n", "/tmp/bar");

    }
}
