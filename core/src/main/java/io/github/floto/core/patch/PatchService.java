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
import io.github.floto.dsl.model.Image;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;
import jersey.repackaged.com.google.common.collect.Lists;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.slf4j.Logger;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.slf4j.LoggerFactory.getLogger;

public class PatchService {
    private final Logger log = getLogger(PatchService.class);
    private final ObjectMapper objectMapper;

    private File patchesDirectory;
    private FlotoService flotoService;
    private TaskService taskService;
    private ImageRegistry imageRegistry;
    private PatchInfo activePatch = null;


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
        // TODO: upload parent patch

        // TODO build images
        Manifest manifest = flotoService.getManifest();

        Instant creationDate = Instant.now();
        File sitePatchesDirectory = getSitePatchesDirectory(manifest);
        File tempDir = new File(sitePatchesDirectory, ".tmp-" + UUID.randomUUID());
        FileUtils.forceMkdir(tempDir);

        Host host = manifest.hosts.get(0);
        WebTarget dockerTarget = flotoService.createDockerTarget(host);

        LinkedHashSet<String> imageNames = new LinkedHashSet<>(Lists.transform(manifest.containers, (container) -> container.image));

        // TODO: remove
//        imageNames.clear();
//        imageNames.add("dns");
        Host patchMakerHost = manifest.findHost("patch-maker");

        int imageNumber = 1;

        for(String imageName: imageNames) {
            log.info("Building image {} ({}/{})", imageName, imageNumber, imageNames.size());
            imageNumber++;
            Image image = manifest.findImage(imageName);
            LogOutputStream logStream = new LogOutputStream() {
                @Override
                protected void processLine(String s, int i) {
                    log.info(">> " + s);
                }
            };
            flotoService.createImage(patchMakerHost, image, System.out);
        }

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

        String siteName = manifest.site.get("projectName").asText();
        String revision = manifest.site.get("projectRevision").asText();
        String patchDirName = safeFilename(creationDate.toString()) + "-" + safeFilename(revision);
        String patchId = patchDirName + "." + safeFilename(manifest.getSiteName());


        PatchDescription patchDescription = new PatchDescription();
        patchDescription.id = patchId;
        patchDescription.creationDate = creationDate;
        patchDescription.siteName = siteName;

        Path pathAbsolute = Paths.get("/var/data/stuff/xyz.dat");
        Path pathBase = Paths.get("/var/data");
        Path pathRelative = pathBase.relativize(pathAbsolute);

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder.readEnvironment().findGitDir(flotoService.getRootDefinitionFile()).readEnvironment().build();

        Path confDirectory = repository.getWorkTree().toPath();
        Path rootDefinitionPath = flotoService.getRootDefinitionFile().toPath();

        patchDescription.rootDefinitionFile = confDirectory.relativize(rootDefinitionPath).toString();
        patchDescription.revision = revision;
        patchDescription.requiredImageIds.addAll(allRequiredImageIds);

        patchDescription.containedImageIds.addAll(allRequiredImageIds);

        if (parentPatchId != null) {
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
            FileTreeIterator fileTreeIterator = new FileTreeIterator(repository);
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(fileTreeIterator);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                WorkingTreeIterator f = treeWalk.getTree(0, WorkingTreeIterator.class);
                if (!f.isEntryIgnored()) {
                    addEntryToZipFile(patchOutputStream, "conf/" + treeWalk.getPathString(), f.openEntryStream());
                    FileUtils.copyFile(new File(repository.getWorkTree(), treeWalk.getPathString()), new File(tempDir, "conf/" + treeWalk.getPathString()));

                }
            }
            // copy .git directory
            FileUtils.copyDirectory(repository.getDirectory(), new File(tempDir, "conf/.git"));

            Collection<File> files = FileUtils.listFiles(
                    repository.getDirectory(),
                    FileFilterUtils.fileFileFilter(),
                    DirectoryFileFilter.DIRECTORY
            );
            Path gitPath = repository.getDirectory().toPath();

            for (File file : files) {
                Path relativePath = gitPath.relativize(file.toPath());
                addEntryToZipFile(patchOutputStream, "conf/.git/" + relativePath.toString(), new FileInputStream(file));
            }

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
        File[] siteDirectories = patchesDirectory.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
        if (siteDirectories == null) {
            throw new IllegalStateException("No site directories found");
        }
        for (File siteDirectory : siteDirectories) {
            File patchDirectory = new File(siteDirectory, patchId);
            if (patchDirectory.exists()) {
                return patchDirectory;
            }
        }

        throw new IllegalStateException("Patch directory not found: " + patchId);
    }

    private static Pattern sanitarizationPattern = Pattern.compile("[^a-zA-Z0-9\\-_.]");

    private static String safeFilename(String unsafeFilename) {
        return sanitarizationPattern.matcher(unsafeFilename).replaceAll("-");
    }

    public PatchesInfo getPatches() {
        PatchesInfo patchesInfo = new PatchesInfo();
        List<File> siteDirectories = new ArrayList<>();
        List<File> patchDirectories = new ArrayList<>();
        if (flotoService.getRootDefinitionFile() != null) {
            // only add single site patches
            Manifest manifest = flotoService.getManifest();
            siteDirectories.add(getSitePatchesDirectory(manifest));
        } else {
            // should only have a single site
            File[] allSiteDirectories = patchesDirectory.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
            if (allSiteDirectories != null) {
                siteDirectories.addAll(Arrays.asList(allSiteDirectories));
            }

        }
        for (File siteDirectory : siteDirectories) {
            File[] directories = siteDirectory.listFiles(File::isDirectory);
            if (directories != null) {
                patchDirectories.addAll(Arrays.asList(directories));
            }
        }


        for (File directory : patchDirectories) {
            if (directory.getName().startsWith(".")) {
                // skip "hidden" directories
                continue;
            }
            try {
                File patchDescriptionFile = getPatchDescriptionFile(directory);
                PatchDescription patchDescription = objectMapper.readValue(patchDescriptionFile, PatchDescription.class);
                patchesInfo.patches.add(patchDescription);
            } catch (Throwable throwable) {
                log.warn("Error reading patch in directory " + directory, throwable);
            }
        }
        patchesInfo.patches.sort((PatchDescription a, PatchDescription b) -> -a.creationDate.compareTo(b.creationDate));
        if (activePatch != null) {
            patchesInfo.activePatchId = activePatch.id;
        }
        return patchesInfo;

    }

    public TaskInfo<Void> activatePatch(String patchId) {
        return taskService.startTask("Activate patch " + patchId, () -> {
            activePatch = getPatchInfo(patchId);
            File patchDirectory = getPatchDirectory(activePatch.id);
            flotoService.setRootDefinitionFile(new File(patchDirectory, "conf/" + activePatch.rootDefinitionFile));
            flotoService.setActivePatch(activePatch);
            flotoService.compileManifest().getResultFuture().get();
            return null;
        });
    }

    public TaskInfo<Void> uploadPatch(String filename, InputStream inputStream) {
        File tempDir = new File(patchesDirectory, ".tmp-patch-" + UUID.randomUUID());
        File patchFile = new File(tempDir, "patch.zip");
        try {
            FileUtils.forceMkdir(tempDir);
            FileUtils.copyInputStreamToFile(inputStream, patchFile);
        } catch (Throwable throwable) {
            Throwables.propagate(throwable);
        }
        return taskService.startTask("Upload patch " + filename, () -> {
            try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(patchFile))) {
                ZipEntry nextEntry;
                while ((nextEntry = zipInputStream.getNextEntry()) != null) {
                    log.trace("File: " + nextEntry.getName());

                    FileUtils.copyInputStreamToFile(new CloseShieldInputStream(zipInputStream), new File(tempDir, nextEntry.getName()));
                }
            }
            File patchDescriptionFile = new File(tempDir, "patch-description.json");
            PatchInfo patchInfo = objectMapper.readValue(patchDescriptionFile, PatchInfo.class);
            log.info("Uploaded patch revision: " + patchInfo.revision);
            log.info("Uploaded patch site: " + patchInfo.siteName);

            File layersDirectory = new File(tempDir, "images");
            if (layersDirectory.exists()) {
                File[] layerDirectories = layersDirectory.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
                for (File layerDirectory : layerDirectories) {
                    File destinationDirectory = imageRegistry.getImageDirectory(layerDirectory.getName());
                    if (destinationDirectory.exists()) {
                        FileUtils.deleteDirectory(destinationDirectory);
                    }
                    FileUtils.moveDirectory(layerDirectory, destinationDirectory);
                }
            }

            // Rename patch file
            File newPatchFile = new File(tempDir, patchInfo.id + ".floto-patch.zip");
            FileUtils.moveFile(patchFile, newPatchFile);

            // Move to patch directory
            File patchDirectory = new File(new File(patchesDirectory, safeFilename(patchInfo.siteName)), patchInfo.id);
            FileUtils.forceMkdir(patchDirectory.getParentFile());
            if (patchDirectory.exists()) {
                FileUtils.deleteDirectory(patchDirectory);
            }
            FileUtils.moveDirectory(tempDir, patchDirectory);
            return null;
        });
    }


    @FunctionalInterface
    public interface Consumer_WithExceptions<T> {
        void accept(T t) throws Exception;
    }

}
