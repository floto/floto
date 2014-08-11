package io.github.floto.dsl.util;

import com.google.common.base.Throwables;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileReader;

public class MavenUtils {
    public static String getVersion(String directory) {
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        try {
            File pomFile = new File(directory + "/pom.xml");
            FileReader reader = new FileReader(pomFile);
            Model model = mavenReader.read(reader);
            model.setPomFile(pomFile);
            MavenProject project = new MavenProject(model);
            return project.getVersion();
        } catch (Exception ex) {
            throw Throwables.propagate(ex);
        }
    }
}
