package general.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import org.apache.commons.lang3.SystemUtils;
import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import play.Environment;
import play.Logger;
import play.inject.ApplicationLifecycle;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import scala.concurrent.ExecutionContext;
import scala.jdk.javaapi.FutureConverters;
import utils.common.IOUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// @formatter:off
/**
 * This class handles JATOS updates. The update process is split between this JatosUpdater and loader.sh. JatosUpdater
 * prepares the update while the old JATOS process is still running. loader.sh performs the final file replacement after
 * JATOS has stopped. So far the updater only supports Linux OS.
 *
 * Some hints:
 * - JATOS releases are currently stored at GitHub
 * - The data requested from GitHub are stored in ReleaseInfo
 * - If there is 'nau' (no auto update) anywhere in the release's version, an update is forbidden. This is a safety
 * feature in case a future update is not compatible with this update process. In JATOS < 3.11.x it was just an 'n'.
 * - The new release might need a new Java version. The asset's filename determines the release's Java version
 * (see newJavaVersion).
 * - The current state of the update process (UpdateState) is stored in 'state'.
 * - In the GUI the update is handled in the administration view
 *
 * Update process:
 * 1. Check GitHub for new releases and put release data into ReleaseInfo
 * 2. Ask the user (GUI home view)
 * 3. Download and unzip a new release into the system's tmp folder
 * 4. Ask the user (GUI home view)
 * 5. Move the new release into a separate folder within the current JATOS installation folder
 * 6. Exchange loader scripts and config folder
 * 7. Restart JATOS: finish the process with exit code 46. That tells the loader.sh that an update has to be continued.
 * 8. The loader script moves everything in the new release folder into the JATOS installation folder (eventually
 * overwriting existing files)
 * 9. The loader script starts JATOS again
 * 10. JATOS shows a success msg (or a failure msg)
 */
// @formatter:on
@Singleton
public class JatosUpdater {

    private static final Logger.ALogger LOGGER = Logger.of(JatosUpdater.class);
    private static final String BACKUP_DIR_PREFIX = "backup_";
    private static final int UPDATE_RESTART_EXIT_CODE = 46;

    enum UpdateState {
        SLEEPING, // most of the time
        DOWNLOADING, // Currently downloading new release
        DOWNLOADED, // Finished downloading and unzipping successfully
        MOVING, // In the process of moving files into the current JATOS installation folder
        RESTARTING, // Stopping the JATOS process and restarting the loader script with 'update' parameter
        SUCCESS, // Finished restart (with 'update') successfully
        FAILED // Something gone wrong during restart with 'update'
    }

    /**
     * Initial state after every JATOS start is SLEEPING, unless Initializer sets it to SUCCESS or FAILED
     */
    UpdateState state = UpdateState.SLEEPING;

    /**
     * Last time the release info was requested from GitHub
     */
    private LocalTime lastTimeAskedReleaseInfo;

    ReleaseInfo currentReleaseInfo;

    /**
     * Contains all info about an JATOS update. It's also sent as JSON to the GUI. Fields have to be public for JSON
     * serialization.
     */
    @SuppressWarnings("WeakerAccess")
    static class ReleaseInfo {

        /**
         * Version of the currently installed JATOS like in GitHub, e.g., v3.5.5-alpha
         */
        public final String currentVersionFull;

        /**
         * Version of the currently installed one in format x.x.x
         */
        public final String currentVersion;

        /**
         * Version of JATOS like in GitHub, e.g. v3.5.5-alpha
         */
        public final String versionFull;

        /**
         * Version of JATOS in format x.x.x
         */
        public final String version;

        /**
         * Is it a pre-release?
         */
        public final boolean isPrerelease;

        /**
         * Is it the latest version?
         */
        public final boolean isLatest;

        /**
         * Download URLs to zip files
         */
        public String zipUrl;
        public String zipJavaUrl;

        /**
         * Size in byte of the JATOS zip files
         */
        public int zipSize;
        public int zipJavaSize;

        /**
         * Description of the release in Markup
         */
        public final String releaseNotes;

        /**
         * Is the version of this release newer than the currently installed?
         */
        public final boolean isNewerVersion;

        /**
         * Versions with an 'n' in the name are not allowed to be updated automatically. This is a safety switch if an
         * future update isn't compatible with this way of update. Additionally, JATOS on Windows doesn't allow
         * automatic updates.
         */
        public boolean isUpdateAllowed;

        /**
         * Java version needed for the release. It's determined from the asset's filename: everything between 'java' and
         * '.zip', e.g. 'jatos-3.4.1_linux_java1.8.zip' -> '1.8'
         */
        public String newJavaVersion;

        /**
         * If a newJavaVersion is different from the currently installed one, it's automatically a newer Java version.
         */
        public boolean isDifferentJava;

        ReleaseInfo(JsonNode jsonNode, boolean isLatest) {
            this.isLatest = isLatest;
            versionFull = jsonNode.get("tag_name").asText();
            version = versionFull.replaceAll("[^\\d.]", "");
            currentVersionFull = Common.getJatosVersion();
            currentVersion = currentVersionFull.replaceAll("[^\\d.]", "");
            isPrerelease = jsonNode.get("prerelease").asBoolean();
            releaseNotes = jsonNode.get("body").asText();
            isNewerVersion = compareVersions(version, currentVersion) == 1;
            isUpdateAllowed = isOsUx() && !versionFull.toLowerCase(Locale.ROOT).contains("nau");
            jsonNode.get("assets").forEach(this::getFieldsFromAsset);
        }

        /**
         * Compare two JATOS versions (major.minor.patch)
         *
         * Returns -1 if version1 is older than version2 Returns 0 if version1 is equal to version2 Returns 1 if
         * version1 is newer than version2
         */
        private int compareVersions(String version1, String version2) {
            String[] p1 = version1.split("\\.");
            String[] p2 = version2.split("\\.");
            int major1 = Integer.parseInt(p1[0]);
            int major2 = Integer.parseInt(p2[0]);
            if (major1 < major2) return -1;
            if (major1 > major2) return 1;
            int minor1 = Integer.parseInt(p1[1]);
            int minor2 = Integer.parseInt(p2[1]);
            if (minor1 < minor2) return -1;
            if (minor1 > minor2) return 1;
            int patch1 = Integer.parseInt(p1[2]);
            int patch2 = Integer.parseInt(p2[2]);
            return Integer.compare(patch1, patch2);
        }

        /**
         * Gets zip files' download URLs and sizes, and Java version of the release. Per release there are usually two
         * kinds of zips, one without Java and one with.
         */
        private void getFieldsFromAsset(JsonNode asset) {
            String filename = asset.get("name").asText();
            if (!filename.endsWith(".zip")) return;

            if ((SystemUtils.IS_OS_LINUX && filename.contains("linux"))
                    || (isMacAarch64() && filename.contains("mac_aarch64"))
                    || (isMacX64() && filename.contains("mac_x64"))
                    || (SystemUtils.IS_OS_WINDOWS && filename.contains("win"))) {
                zipJavaUrl = asset.get("browser_download_url").asText();
                zipJavaSize = asset.get("size").asInt();
                newJavaVersion = getAssetsJavaVersion(filename);
                isDifferentJava = newJavaVersion != null &&
                        !newJavaVersion.equals(System.getProperty("java.specification.version"));
            } else if (!filename.contains("linux") && !filename.contains("mac") && !filename.contains("win")) {
                zipUrl = asset.get("browser_download_url").asText();
                zipSize = asset.get("size").asInt();
            }
        }

        private String getAssetsJavaVersion(String filename) {
            Pattern p = Pattern.compile("java(.+).zip"); // Everything between 'java' and '.zip' is Java version
            Matcher m = p.matcher(filename);
            return m.find() ? m.group(1) : null;
        }
    }

    /**
     * Determine the path and name of the directory where the update files will be stored.
     */
    private final Supplier<Path> tmpJatosDir = () -> IOUtils.tmpDir().resolve(
            "jatos-" + currentReleaseInfo.versionFull);

    private final WSClient ws;

    private final Materializer materializer;

    private final ActorSystem actorSystem;

    private final ExecutionContext executionContext;

    private final ApplicationLifecycle applicationLifecycle;

    private final Environment environment;

    private final ObjectMapper objectMapper;

    @Inject
    JatosUpdater(WSClient ws,
                 Materializer materializer,
                 ActorSystem actorSystem,
                 ExecutionContext executionContext,
                 ApplicationLifecycle applicationLifecycle,
                 Environment environment,
                 ObjectMapper objectMapper) {
        this.ws = ws;
        this.materializer = materializer;
        this.actorSystem = actorSystem;
        this.executionContext = executionContext;
        this.applicationLifecycle = applicationLifecycle;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public void setUpdateStateSuccess() {
        this.state = UpdateState.SUCCESS;
    }

    public void setUpdateStateFailed() {
        this.state = UpdateState.FAILED;
    }

    private void resetUpdateState() {
        if (state == UpdateState.SUCCESS || state == UpdateState.FAILED) {
            state = UpdateState.SLEEPING;
        }
    }

    public CompletionStage<JsonNode> getReleaseInfo(String version, boolean allowPreReleases) {
        return fetchReleaseInfo(version, allowPreReleases).thenApply(releaseInfo -> {
            currentReleaseInfo = releaseInfo;
            ObjectNode json = objectMapper.valueToTree(currentReleaseInfo);
            json.put("currentUpdateState", state.toString());
            resetUpdateState();
            return json;
        });
    }

    /**
     * Gets JATOS release information from GitHub and returns it as a String in format x.x.x. To prevent a high load on
     * GitHub, it stores it locally and newly requests it only once per hour (only if allowPreReleases is false). If no
     * version is given, it gets the latest one.
     *
     * @param version          Which release info to get. If null, the latest is fetched.
     * @param allowPreReleases If true, it includes pre-releases.
     */
    private CompletionStage<ReleaseInfo> fetchReleaseInfo(String version, boolean allowPreReleases) {
        if (version != null) {
            return requestReleaseInfo("https://api.github.com/repos/JATOS/JATOS/releases/tags/" + version, false);
        }

        boolean notOlderThanAnHour = lastTimeAskedReleaseInfo != null && LocalTime.now().minusHours(1).isBefore(
                lastTimeAskedReleaseInfo);
        if (currentReleaseInfo != null && notOlderThanAnHour && !allowPreReleases && currentReleaseInfo.isLatest) {
            return CompletableFuture.completedFuture(currentReleaseInfo);
        }
        if (allowPreReleases) {
            return requestLatestReleaseInfoInclPre();
        } else {
            return requestReleaseInfo("https://api.github.com/repos/JATOS/JATOS/releases/latest", true);
        }
    }

    private CompletionStage<ReleaseInfo> requestReleaseInfo(String url, boolean islatest) {
        return ws.url(url).get().thenApply(res -> {
            JsonNode json = res.asJson();
            ReleaseInfo releaseInfo = new ReleaseInfo(json, islatest);
            if (islatest) LOGGER.info("Checked GitHub for latest release of JATOS: " + releaseInfo.versionFull);
            else LOGGER.info("Checked GitHub for release of JATOS: " + releaseInfo.versionFull);
            lastTimeAskedReleaseInfo = LocalTime.now();
            return releaseInfo;
        });
    }

    private CompletionStage<ReleaseInfo> requestLatestReleaseInfoInclPre() {
        String url = "https://api.github.com/repos/JATOS/JATOS/releases";
        return ws.url(url).get().thenApply(res -> {
            JsonNode first = res.asJson().get(0);
            ReleaseInfo releaseInfo = new ReleaseInfo(first, true);
            String msg =
                    "Checked GitHub for latest release of JATOS (allowing pre-release): " + releaseInfo.versionFull;
            if (releaseInfo.isPrerelease) msg += " (pre-release)";
            LOGGER.info(msg);
            lastTimeAskedReleaseInfo = LocalTime.now();
            return releaseInfo;
        });
    }

    public void cancelUpdate() {
        state = UpdateState.SLEEPING;
    }

    public CompletionStage<?> downloadFromGitHubAndUnzip(boolean dry) {
        if (currentReleaseInfo == null) {
            return failedStage(new IllegalStateException(
                    "No JATOS release information is available. Check for updates before downloading."));
        }

        if (!currentReleaseInfo.isUpdateAllowed) {
            return failedStage(new IllegalStateException("Can't update to version "
                    + currentReleaseInfo.versionFull
                    + " automatically. This JATOS release has to be updated manually."));
        }

        if (state != UpdateState.SLEEPING) {
            return failedStage(new IllegalStateException(busyStateMessage(state)));
        }

        if (dry) {
            state = UpdateState.DOWNLOADED;
            LOGGER.info("Dry download");
            return CompletableFuture.completedFuture(null);
        }

        state = UpdateState.DOWNLOADING;
        final String downloadUrl = currentReleaseInfo.isDifferentJava
                ? currentReleaseInfo.zipJavaUrl
                : currentReleaseInfo.zipUrl;
        final String zipFilename = "jatos-" + currentReleaseInfo.versionFull + ".zip";
        try {
            final CompletionStage<Path> downloadStage = downloadAsync(downloadUrl, zipFilename);
            return downloadStage
                    .thenApply(zipFile -> {
                        Path unzippedDir = ZipUtil.unzip(zipFile, tmpJatosDir.get());
                        state = UpdateState.DOWNLOADED;
                        scheduleStateReset();
                        LOGGER.info("Downloaded and unzipped new JATOS " + zipFilename);
                        return unzippedDir;
                    })
                    .whenComplete((ok, ex) -> {
                        if (ex != null) {
                            state = UpdateState.SLEEPING;
                        }
                    });
        } catch (Exception e) {
            state = UpdateState.SLEEPING;
            return failedStage(e);
        }
    }

    private static String busyStateMessage(UpdateState state) {
        return switch (state) {
            case DOWNLOADING -> "A JATOS update is already downloading.";
            case DOWNLOADED -> "A JATOS update was already downloaded.";
            default -> "Wrong update state";
        };
    }

    private static <T> CompletionStage<T> failedStage(Throwable t) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(t);
        return future;
    }

    private CompletionStage<Path> downloadAsync(String url, String filename) throws IOException {
        Path file = IOUtils.tmpDir().resolve(filename);
        OutputStream outputStream = Files.newOutputStream(file);
        LOGGER.info("Download " + url);

        CompletionStage<WSResponse> futureResponse = ws.url(url)
                .setMethod("GET")
                .setRequestTimeout(Duration.ofHours(1))
                .stream();

        return futureResponse.thenCompose(res -> {
            Source<ByteString, ?> responseBody = res.getBodyAsSource();
            Sink<ByteString, CompletionStage<Done>> outputWriter = Sink.foreach(
                    bytes -> outputStream.write(bytes.toArray()));
            return responseBody.runWith(outputWriter, materializer).thenApply(v -> file);
        }).whenComplete((path, throwable) -> {
            try {
                outputStream.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close downloaded file output stream for " + file, e);
            }

            if (throwable != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete incomplete download file " + file, e);
                }
            }
        });
    }

    /**
     * One update once initialized (left state SLEEPING) can last max 1 hour. Then the state is reset back to SLEEPING.
     */
    private void scheduleStateReset() {
        actorSystem.scheduler().scheduleOnce(Duration.ofHours(1), this::cancelUpdate, this.executionContext);
    }

    /**
     * Backups, updates files and restarts JATOS
     *
     * @param backupAll If true, everything in the JATOS directory will be copied into a backup folder. If false, only
     *                  the conf directory and the loader scripts.
     */
    public void updateAndRestart(boolean backupAll) throws IOException {
        if (state == UpdateState.MOVING || state == UpdateState.RESTARTING) {
            return;
        }
        if (!currentReleaseInfo.isUpdateAllowed) {
            throw new IllegalStateException("Can't update to version " + currentReleaseInfo.versionFull
                    + " automatically. This JATOS release has to be updated manually.");
        }
        if (state != UpdateState.DOWNLOADED) {
            throw new IllegalStateException("Wrong update state (" + state + ")");
        }
        if (!Files.isDirectory(tmpJatosDir.get())) {
            state = UpdateState.SLEEPING;
            throw new IOException("JATOS update directory couldn't be found in " + tmpJatosDir.get().toAbsolutePath());
        }

        if (!environment.isProd()) {
            LOGGER.warn("JATOS is not in production mode. Update will be canceled.");
            cancelUpdate();
            return;
        }

        // Execute backup, update and restart in a shutdown hook. This ensures that all resources have been closed
        // beforehand (e.g. a database closed and no more changing of study assets). It's especially important that the
        // H2 doesn't write into its files anymore; otherwise they might get corrupted.
        applicationLifecycle.addStopHook(() -> {
            state = UpdateState.MOVING;
            backupCurrentJatosFiles(backupAll);
            updateFiles();

            state = UpdateState.RESTARTING;
            return CompletableFuture.completedFuture(null);
        });

        LOGGER.info("Restart JATOS to finish update to version " + currentReleaseInfo.versionFull);
        // First stop Play and then System.exit
        FutureConverters
                .asJava(actorSystem.terminate())
                .thenAccept((t) -> System.exit(UPDATE_RESTART_EXIT_CODE));
    }

    /**
     * Makes backups of current JATOS files. Depending on configOnly copies only loader scripts and config/ - or
     * everything.
     */
    private void backupCurrentJatosFiles(boolean backupAll) throws IOException {
        String bkpDirName = BACKUP_DIR_PREFIX + Common.getJatosVersion();
        Path bkpDir = Path.of(Common.getBasepath(), bkpDirName);
        int i = 2;
        while (Files.exists(bkpDir)) {
            bkpDir = Path.of(Common.getBasepath(), bkpDirName + "_" + i);
            i++;
        }
        Files.createDirectories(bkpDir);

        if (backupAll) {
            DirectoryStream.Filter<Path> filter = entry -> {
                String name = entry.getFileName().toString();
                return !name.equals("RUNNING_PID") && !name.startsWith(BACKUP_DIR_PREFIX);
            };
            IOUtils.copyRecursively(Path.of(Common.getBasepath()), bkpDir, filter);
            LOGGER.info("Backup of current JATOS files into " + bkpDir.getFileName());
        } else {
            IOUtils.copyRecursively(Path.of(Common.getBasepath(), "conf"), bkpDir.resolve("conf"));
            Files.copy(Path.of(Common.getBasepath(), "loader.sh"), bkpDir.resolve("loader.sh"));
            // JATOS Docker has no loader.bat
            if (Files.exists(Path.of(Common.getBasepath(), "loader.bat"))) {
                Files.copy(Path.of(Common.getBasepath(), "loader.bat"), bkpDir.resolve("loader.bat"));
            }
            LOGGER.info("Backup of current version of loader scripts and conf/ into " + bkpDir.getFileName());
        }
    }

    private void updateFiles() throws IOException {
        Path srcUpdateDir;
        try (Stream<Path> stream = Files.list(tmpJatosDir.get())) {
            srcUpdateDir = stream.findFirst().orElseThrow(
                    () -> new FileNotFoundException("JATOS update directory seems to be corrupted."));
        }

        Path dstUpdateDir = Path.of(Common.getBasepath(), "update-" + currentReleaseInfo.versionFull);
        if (Files.exists(dstUpdateDir)) {
            IOUtils.deleteRecursively(dstUpdateDir);
            LOGGER.info("Deleted old update directory " + dstUpdateDir);
        }

        IOUtils.copyRecursively(srcUpdateDir, dstUpdateDir);
        LOGGER.info("Copied JATOS update files into JATOS installation folder under " + dstUpdateDir);

        updateLoaderScripts(dstUpdateDir);

        IOUtils.deleteRecursively(tmpJatosDir.get());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void updateLoaderScripts(Path srcDir) {
        try {
            IOUtils.moveFile(srcDir.resolve("loader.sh"), Path.of(Common.getBasepath(), "loader.sh"), true);
            IOUtils.moveFile(srcDir.resolve("loader.bat"), Path.of(Common.getBasepath(), "loader.bat"), true);
            Path.of(Common.getBasepath(), "loader.sh").toFile().setExecutable(true);
            Path.of(Common.getBasepath(), "loader.bat").toFile().setExecutable(true);
            LOGGER.info("Replaced loader scripts with newer version.");
        } catch (IOException e) {
            throw new JatosException(e.getMessage(), e, ErrorCode.IO_ERROR);
        }
    }

    /**
     * Returns true if the OS JATOS is running on is either Linux or Unix (macOS) - and false otherwise.
     */
    private static boolean isOsUx() {
        return SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_UNIX;
    }

    private static boolean isMacAarch64() {
        return SystemUtils.IS_OS_MAC && isAarch64();
    }

    private static boolean isMacX64() {
        return SystemUtils.IS_OS_MAC && isX64();
    }

    private static boolean isAarch64() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.equals("aarch64") || arch.equals("arm64");
    }

    private static boolean isX64() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.equals("x86_64") || arch.equals("amd64");
    }

}
