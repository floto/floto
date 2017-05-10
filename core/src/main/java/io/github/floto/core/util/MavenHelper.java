package io.github.floto.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenHelper {
	private static Logger log = LoggerFactory.getLogger(MavenHelper.class);
	private static String m2Dir;
    List<RemoteRepository> remoteRepositories = new ArrayList<>();

	static {
		String userHome = System.getProperty("user.home");
		m2Dir = System.getenv("M2_DIR");
		if (m2Dir == null) {
			m2Dir = userHome + "/.m2";
		}
		log.info("Using M2 directory: {}", m2Dir);
	}

    public MavenHelper(JsonNode repositories) {
		if (repositories == null || repositories.isMissingNode()) {
            RemoteRepository central = new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();
            remoteRepositories.add(central);
        } else {
            for(JsonNode repository: repositories) {
                if(repository.isTextual()) {
                    RemoteRepository remoteRepository = new RemoteRepository.Builder(null, "default", repository.asText()).build();
                    remoteRepositories.add(remoteRepository);
                } else {
                    throw new IllegalArgumentException("Can't handle repository node. "+ repository);
                }
            }
        }

    }

    public File resolveMavenDependency(String coordinates) {
        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository localRepo = new LocalRepository(m2Dir + "/repository");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        Artifact artifact = new DefaultArtifact(coordinates);

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        artifactRequest.setRepositories(remoteRepositories);
        try {
            ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
            InstallRequest ins = new InstallRequest();
            ins.addArtifact(artifactResult.getArtifact());
            return artifactResult.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException(e);
        } catch (ProcessingException pe ){
        	log.warn("Failed to get artifact from remote repositories, using local repo. " + pe.getLocalizedMessage());
			try {
				ArtifactResult artifactResult = system.resolveArtifact(session, (new ArtifactRequest().setArtifact(artifact)));
				InstallRequest ins = new InstallRequest();
				ins.addArtifact(artifactResult.getArtifact());
				return artifactResult.getArtifact().getFile();
			} catch (ArtifactResolutionException e) {
				throw new RuntimeException(e);
			}
		}
    }

    private RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        return locator.getService(RepositorySystem.class);
    }
}
