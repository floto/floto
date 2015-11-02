package io.github.floto.core.patch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.google.common.base.Throwables;
import com.google.inject.util.Types;
import io.github.floto.core.FlotoService;
import io.github.floto.core.registry.DockerImageDescription;
import io.github.floto.core.registry.ImageRegistry;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;
import jersey.repackaged.com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.slf4j.Logger;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.slf4j.LoggerFactory.getLogger;

public class PatchService {
    private final Logger log = getLogger(PatchService.class);
    private final ObjectMapper objectMapper;

    private File patchesDirectory;
    private FlotoService flotoService;
    private TaskService taskService;
    private ImageRegistry imageRegistry;


    public PatchService(File patchesDirectory, FlotoService flotoService, TaskService taskService, ImageRegistry imageRegistry) {
        this.patchesDirectory = patchesDirectory;
        this.flotoService = flotoService;
        this.taskService = taskService;
        this.imageRegistry = imageRegistry;

        try {
            FileUtils.forceMkdir(patchesDirectory);
        } catch (IOException e) {
            Throwables.propagate(e);
        }

        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JSR310Module());

    }

    private File getSitePatchesDirectory(Manifest manifest) {
        String siteName = manifest.getSiteName();
        return new File(patchesDirectory, safeFilename(siteName));
    }

    public void createPatch(String parentPatchId, Set<String> existingImageIds) throws Exception {
        // TODO: tempdir
        Instant creationDate = Instant.now();
        Manifest manifest = flotoService.getManifest();
        File sitePatchesDirectory = getSitePatchesDirectory(manifest);
        File tempDir = new File(sitePatchesDirectory, ".tmp-" + UUID.randomUUID());
        FileUtils.forceMkdir(tempDir);

        Host host = manifest.hosts.get(0);
        WebTarget dockerTarget = flotoService.createDockerTarget(host);

        LinkedHashSet<String> imageNames = new LinkedHashSet<>(Lists.transform(manifest.containers, (container) -> container.image));

        // TODO: remove
        imageNames.clear();
        imageNames.add("dns");

        List<DockerImageDescription> imageDescriptions = dockerTarget.path("/images/json").queryParam("all", "1").request().buildGet().submit(new GenericType<List<DockerImageDescription>>(Types.listOf(DockerImageDescription.class))).get();
        // Map image descriptions to image names
        Map<String, DockerImageDescription> imageDescriptionMap = new HashMap<>();
        Map<String, DockerImageDescription> imageDescriptionIdMap = new HashMap<>();
        for (DockerImageDescription imageDescription : imageDescriptions) {
            imageDescriptionIdMap.put(imageDescription.Id, imageDescription);
            for (String tag : imageDescription.RepoTags) {
                imageDescriptionMap.put(tag, imageDescription);
            }
        }

        List<String> imageNamesToDownload = new ArrayList<>();
        List<String> imageNamesToSkip = new ArrayList<>();
        Set<String> allRequiredImageIds = new HashSet<>();
        Map<String, String> imageMap = new HashMap<String, String>();
        log.info("images: {}", imageMap);

        for (String imageName : imageNames) {
            boolean haveAllImages = true;
            // TODO: check build hash match
            DockerImageDescription imageDescription = imageDescriptionMap.get(imageName + "-image:latest");
            if (imageDescription == null) {
                throw new IllegalStateException("No docker image found for " + imageName);
            }
            String imageId = imageDescription.Id;
            imageMap.put(imageName, imageId);
            while (imageId != null) {
                if (!imageRegistry.hasImage(imageId)) {
                    haveAllImages = false;
                }
                allRequiredImageIds.add(imageId);
                imageDescription = imageDescriptionIdMap.get(imageId);
                if (imageDescription == null || imageDescription.ParentId.isEmpty()) {
                    break;
                } else {
                    imageId = imageDescription.ParentId;
                }
            }
            if (haveAllImages) {
                imageNamesToSkip.add(imageName);
            } else {
                imageNamesToDownload.add(imageName);
            }
        }
        log.info("Required image ids {}", allRequiredImageIds);
        log.info("Skipping images (already in local image registry): {}", imageNamesToSkip);
        WebTarget webTarget = dockerTarget.path("/images/get").queryParam("names", Lists.transform(imageNamesToDownload, name -> name + "-image:latest").toArray());
        log.info("Retrieving images: {}", imageNamesToDownload);
        Response response = webTarget.request().buildGet().invoke();
        try (InputStream imageTarballInputStream = response.readEntity(InputStream.class)) {
            imageRegistry.storeImages(imageTarballInputStream);
        }
        ;

        // Genesis patch: all images

        // Other patches: delta to existing images

        String siteName = manifest.site.get("projectName").asText();
        String revision = manifest.site.get("projectRevision").asText();
        String patchDirName = safeFilename(creationDate.toString()) + "-" + safeFilename(revision);
        String patchId = patchDirName + "." + safeFilename(manifest.getSiteName());



        PatchDescription patchDescription = new PatchDescription();
        patchDescription.id = patchId;
        patchDescription.creationDate = creationDate;
        patchDescription.siteName = siteName;
        patchDescription.revision = revision;
        patchDescription.requiredImageIds.addAll(allRequiredImageIds);

        patchDescription.containedImageIds.addAll(allRequiredImageIds);

        if(parentPatchId != null) {
            PatchInfo parentPatchInfo = getPatchInfo(parentPatchId);
            patchDescription.parentId = parentPatchId;
            patchDescription.parentRevision = parentPatchInfo.revision;
            // remove image ids already present
            patchDescription.containedImageIds.removeAll(parentPatchInfo.requiredImageIds);
        }
        List<String> containedImageIds = new ArrayList<String>(patchDescription.containedImageIds);


        patchDescription.imageMap = imageMap;

        File patchDescriptionFile = getPatchDescriptionFile(tempDir);
        objectMapper.writeValue(patchDescriptionFile, patchDescription);

        File patchFile = new File(tempDir, patchId + ".floto-patch.zip");
        try (ZipOutputStream patchOutputStream = new ZipOutputStream(new FileOutputStream(patchFile))) {
            // Version
            addEntryToZipFile(patchOutputStream, "VERSION.txt", (outputStream -> IOUtils.write("floto patch v1", outputStream)));

            // Description
            addEntryToZipFile(patchOutputStream, "patch-description.json", new FileInputStream(patchDescriptionFile));

            // Images
            for (String imageId : containedImageIds) {
                File imageDirectory = imageRegistry.getImageDirectory(imageId);
                Path imagePath = imageDirectory.toPath();
                for (File file : FileUtils.listFiles(imageDirectory, TrueFileFilter.TRUE, TrueFileFilter.TRUE)) {
                    Path filePath = file.toPath();
                    Path relativePath = imagePath.relativize(filePath);
                    addEntryToZipFile(patchOutputStream, "images/" + imageId + "/" + relativePath.toString(), new FileInputStream(file));
                }
            }

            // conf
        }
        File sitePatchDirectory = new File(sitePatchesDirectory, patchId);
        FileUtils.moveDirectory(tempDir, sitePatchDirectory);

    }

    public TaskInfo<Void> createFullPatch() {
        return taskService.startTask("Create full patch", () -> {
            createPatch(null, Collections.emptySet());
            return null;
        });
    }

    public TaskInfo<Void> createIncrementalPatch(String parentPatchId) {
        return taskService.startTask("Create incremental patch from " + parentPatchId, () -> {

            createPatch(parentPatchId, Collections.emptySet());
            return null;
        });
    }

    private void addEntryToZipFile(ZipOutputStream zipOutputStream, String filename, Consumer_WithExceptions<OutputStream> writer) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(filename));
        writer.accept(new CloseShieldOutputStream(zipOutputStream));
    }

    private void addEntryToZipFile(ZipOutputStream zipOutputStream, String filename, InputStream inputStream) throws Exception {
        addEntryToZipFile(zipOutputStream, filename, (OutputStream outputStream) -> {
            try {
                IOUtils.copy(inputStream, outputStream);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        });
    }

    private File getPatchDescriptionFile(File sitePatchDirectory) {
        return new File(sitePatchDirectory, "patch-description.json");
    }

    public PatchInfo getPatchInfo(String patchId) {
        try {
            File patchDirectory = getPatchDirectory(patchId);
            File patchDescriptionFile = getPatchDescriptionFile(patchDirectory);
            PatchInfo patchInfo = objectMapper.readValue(patchDescriptionFile, PatchInfo.class);
            patchInfo.patchSize = new File(patchDirectory, patchId + ".floto-patch.zip").length();
            return patchInfo;
        } catch (Throwable throwable) {
            throw new RuntimeException("Error getting patch info for patch id " + patchId, throwable);
        }
    }

    private File getPatchDirectory(String patchId) {
        Manifest manifest = flotoService.getManifest();
        return new File(getSitePatchesDirectory(manifest), patchId);
    }

    private static Pattern sanitarizationPattern = Pattern.compile("[^a-zA-Z0-9\\-_.]");

    private static String safeFilename(String unsafeFilename) {
        return sanitarizationPattern.matcher(unsafeFilename).replaceAll("-");
    }

    public List<PatchDescription> getPatches() {
        Manifest manifest = flotoService.getManifest();
        ArrayList<PatchDescription> patchDescriptions = new ArrayList<>();
        File[] directories = getSitePatchesDirectory(manifest).listFiles(File::isDirectory);
        for (File directory : directories) {
            if (directory.getName().startsWith(".")) {
                // skip "hidden" directories
                continue;
            }
            try {
                File patchDescriptionFile = getPatchDescriptionFile(directory);
                PatchDescription patchDescription = objectMapper.readValue(patchDescriptionFile, PatchDescription.class);
                patchDescriptions.add(patchDescription);
            } catch (Throwable throwable) {
                log.warn("Error reading patch in directory " + directory, throwable);
            }
        }
        patchDescriptions.sort((PatchDescription a, PatchDescription b) -> -a.creationDate.compareTo(b.creationDate));

        return patchDescriptions;

    }

    @FunctionalInterface
    public interface Consumer_WithExceptions<T> {
        void accept(T t) throws Exception;
    }

}
