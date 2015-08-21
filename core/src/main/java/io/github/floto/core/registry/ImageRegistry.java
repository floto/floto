package io.github.floto.core.registry;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

// Client side image registry used to push and pull images
// Uses a directory to store images locally
public class ImageRegistry {
    private final Logger log = getLogger(ImageRegistry.class);

    private File imageDirectory;
    private ObjectMapper objectMapper = new ObjectMapper();

    public ImageRegistry(File imageDirectory) {
        this.imageDirectory = imageDirectory;
        try {
            FileUtils.forceMkdir(imageDirectory);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    public void storeImages(InputStream imageTarballStream) {
        File tempDir = new File(imageDirectory, ".tmp-" + UUID.randomUUID());
        try (TarInputStream tarInputStream = new TarInputStream(imageTarballStream)) {
            FileUtils.forceMkdir(tempDir);
            TarEntry tarEntry;
            // extract images to tempdir
            while (null != (tarEntry = tarInputStream.getNextEntry())) {
                if (!tarEntry.isFile()) {
                    continue;
                }
                File filename = new File(tarEntry.getName());
                String parent = filename.getParent();
                if (parent == null) {
                    continue;
                }
                File directory = new File(tempDir, parent);
                FileUtils.forceMkdir(directory);
                FileUtils.copyInputStreamToFile(new CloseShieldInputStream(tarInputStream), new File(tempDir, tarEntry.getName()));
            }

            // copy to final destination
            File[] directories = tempDir.listFiles(File::isDirectory);
            for (File directory : directories) {
                String name = directory.getName();
                File targetDirectory = new File(imageDirectory, name);
                if (targetDirectory.exists()) {
                    FileUtils.forceDelete(targetDirectory);
                }
                FileUtils.moveDirectory(directory, targetDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error storing images", e);
        } finally {
            FileUtils.deleteQuietly(tempDir);
        }
    }


    public boolean hasImage(String imageId) {
        return getImageDirectory(imageId).isDirectory();
    }

    public File getImageDirectory(String imageId) {
        return new File(imageDirectory, imageId);
    }

    public DockerLayerDescription getImageDescription(String imageId) {
        File layerFile = new File(getImageDirectory(imageId), "json");
        try {
            return objectMapper.readValue(layerFile, DockerLayerDescription.class);
        } catch (Throwable e) {
            throw new RuntimeException("Unable to read image layer file " + layerFile, e);
        }
    }
}
