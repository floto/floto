package io.github.floto.core.ssh;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.InMemorySourceFile;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class SshService {
    static private Logger log = LoggerFactory.getLogger(SshService.class);
	public static final int defaultTimeOut=10;
    static {
        // Disable JCE policy
        // http://stackoverflow.com/questions/3425766/how-would-i-use-maven-to-install-the-jce-unlimited-strength-policy-files
        try {
            Field field = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");
            field.setAccessible(true);
            field.set(null, java.lang.Boolean.FALSE);
        } catch (Throwable e) {
            log.warn("Unable to disable encrpytion restriction", e);
        }

    }

    private final DefaultConfig sshConfig = new DefaultConfig();

	public void execute(String host, String command) {
		execute(host, command, defaultTimeOut);
	}

	public void execute(String host, String command, int timeout){
		log.info("execute(" + host + "," + command + ", timeout: " + timeout + " min.)");
        withSession(host, (session) -> {
            try {
                final Session.Command cmd = session.exec(command);
                try {
                    cmd.join(timeout, TimeUnit.MINUTES);
                } catch (ConnectionException e) {
					log.warn("fail! ", e);
                }
                Integer exitStatus = cmd.getExitStatus();
                if (exitStatus == null) {
                    String stdout = IOUtils.toString(cmd.getInputStream());
                    String stderr = IOUtils.toString(cmd.getErrorStream());
                    throw new RuntimeException("Command timed out on host " + host + ": " + command + "\nStdout:\n" + stdout + "\n\nStderr:\n" + stderr);
                } else if (exitStatus != 0) {
                    String stdout = IOUtils.toString(cmd.getInputStream());
                    String stderr = IOUtils.toString(cmd.getErrorStream());
                    throw new RuntimeException("Command exited with exit code " + exitStatus + " on host " + host + ": " + command + "\nStdout:\n" + stdout + "\n\nStderr:\n" + stderr);
                }
            } catch(Exception e) {
                throw Throwables.propagate(e);
            }
            return null;
        });
	}

    
    public void scp(String host, String contents, String destination) {
		log.info("scp(" + host + ", " + destination + ", " + contents + ")");
        withClient(host, (client) -> {
            try {
                client.newSCPFileTransfer().upload(new InMemorySourceFile() {
                    @Override
                    public String getName() {
                        return "foo";
                    }

                    @Override
                    public long getLength() {
                        return contents.getBytes().length;
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return new ByteArrayInputStream(contents.getBytes());
                    }
                }, destination);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            return null;
        });
    }

    public void scp(String host, File file, String destination) {
		log.info("scp(" + host + ", " + destination + ", " + file.getName() + ")");
        withClient(host, (client) -> {
            try {
                client.newSCPFileTransfer().upload(new InMemorySourceFile() {
                    @Override
                    public String getName() {
                        return "foo";
                    }

                    @Override
                    public long getLength() {
                        return file.length();
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                    	
                        return new FileInputStream(file);
                    }
                }, destination);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            return null;
        });
    }
    private <T> T withSession(String host, Function<Session, T> function) {
        try (final SSHClient client = new SSHClient(sshConfig)) {
            client.addHostKeyVerifier(new PromiscuousVerifier());
            connect(client, host);
            client.authPassword("user", "user");
            try (Session session = client.startSession()) {
                session.setAutoExpand(true);
                return function.apply(session);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void connect(SSHClient client, String host) throws IOException, InterruptedException {
        try {
            client.connect(host);
        } catch(ConnectException ignored) {
            Thread.sleep(1000);
            try {
                client.connect(host);
            } catch(ConnectException ignored2) {
                Thread.sleep(30000);
                client.connect(host);
            }
        }
    }

    private <T> T withClient(String host, Function<SSHClient, T> function) {
        try (final SSHClient client = new SSHClient()) {
            client.addHostKeyVerifier(new PromiscuousVerifier());
            connect(client, host);
            client.authPassword("user", "user");
            return function.apply(client);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

}
