package general.common;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import play.Environment;
import play.Logger;
import play.api.Play;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.ws.WSClient;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import utils.common.IOUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * This class handles JATOS updates. JATOS update is initiated by the GUI. First it checks whether
 * there is a new version available, then it downloads it (if the user is admin), then it moves
 * the update files into the JATOS folder, and finally does a restart. JATOS releases are stored at
 * GitHub. The current state of the update progress is stored in a 'state' variable.
 *
 * @author Kristian Lange (2019)
 */
@Singleton
public class JatosUpdater {

    private static final Logger.ALogger LOGGER = Logger.of(JatosUpdater.class);

    private enum UpdateState {
        SLEEPING, DOWNLOADING, DOWNLOADED, MOVING, RESTARTING
    }

    /**
     * Initial state after every JATOS start is SLEEPING
     */
    private UpdateState state = UpdateState.SLEEPING;

    /**
     * Latest version of JATOS like in GitHub
     */
    private String latestJatosVersionFull;

    /**
     * Latest version of JATOS in format x.x.x
     */
    private String latestJatosVersion;

    private boolean isPrerelease;

    /**
     * Determine the path and name of the directory where the update files will be stored.
     */
    private Supplier<File> tmpJatosDir =
            () -> new File(IOUtils.TMP_DIR, "jatos-" + latestJatosVersionFull);

    /**
     * Last time the latest version info was requested from GitHub
     */
    private LocalTime lastTimeAskedVersion;

    private final WSClient ws;

    private final Materializer materializer;

    private final ActorSystem actorSystem;

    private final ExecutionContext executionContext;

    private final ApplicationLifecycle applicationLifecycle;

    private final Environment environment;

    @Inject
    JatosUpdater(WSClient ws, Materializer materializer, ActorSystem actorSystem,
            ExecutionContext executionContext, ApplicationLifecycle applicationLifecycle,
            Environment environment) {
        this.ws = ws;
        this.materializer = materializer;
        this.actorSystem = actorSystem;
        this.executionContext = executionContext;
        this.applicationLifecycle = applicationLifecycle;
        this.environment = environment;
    }

    public UpdateState getState() {
        return state;
    }

    public CompletionStage<JsonNode> updateInfo(boolean allowPreUpdates) {
        return getLatestVersion(allowPreUpdates).thenApply(f -> {
            boolean isNewerVersion =
                    (compareVersions(latestJatosVersion, Common.getJatosVersion()) == 1);
            boolean isAllowedToUpdate = !latestJatosVersionFull.contains("n");
            return Json.newObject()
                    .put("isNewerVersion", isNewerVersion)
                    .put("isAllowedToUpdate", isAllowedToUpdate)
                    .put("latestJatosVersionFull", latestJatosVersionFull)
                    .put("latestJatosVersion", latestJatosVersion)
                    .put("isPrerelease", isPrerelease)
                    .put("currentJatosVersion", Common.getJatosVersion());
        });
    }

    /**
     * Gets the latest JATOS version information from GitHub and returns it as a String in format
     * x.x.x. To prevent high load on GitHub it stores it locally and newly requests it only once per hour.
     *
     * @param allowPreUpdates If true it includes pre-releases.
     */
    private CompletionStage<?> getLatestVersion(boolean allowPreUpdates) {
        boolean notOlderThanAnHour = lastTimeAskedVersion != null &&
                LocalTime.now().minusHours(1).isBefore(lastTimeAskedVersion);
        if (!Strings.isNullOrEmpty(latestJatosVersionFull) && notOlderThanAnHour) {
            return CompletableFuture.completedFuture(null);
        }

        return allowPreUpdates ? requestLatestVersionInclPre() : requestLatestVersion();
    }

    private CompletionStage<?> requestLatestVersion() {
        String url = "https://api.github.com/repos/JATOS/JATOS/releases/latest";
        return ws.url(url).setRequestTimeout(60000).get().thenAccept(res -> {
            latestJatosVersionFull = res.asJson().findPath("tag_name").asText();
            latestJatosVersion = latestJatosVersionFull.replaceAll("[^\\d.]", "");
            lastTimeAskedVersion = LocalTime.now();
            isPrerelease = false;
            Logger.info("Checked GitHub for latest version of JATOS: " + latestJatosVersionFull);
        });
    }

    private CompletionStage<?> requestLatestVersionInclPre() {
        String url = "https://api.github.com/repos/JATOS/JATOS/releases";
        return ws.url(url).setRequestTimeout(60000).get().thenAccept(res -> {
            JsonNode first = res.asJson().get(0);
            latestJatosVersionFull = first.findPath("tag_name").asText();
            latestJatosVersion = latestJatosVersionFull.replaceAll("[^\\d.]", "");
            lastTimeAskedVersion = LocalTime.now();
            isPrerelease = first.findPath("prerelease").asBoolean();
            String msg = "Checked GitHub for latest version of JATOS: " + latestJatosVersionFull;
            if (isPrerelease) msg += ". It's a prerelease.";
            Logger.info(msg);
        });
    }

    /**
     * Compare two JATOS versions (major.minor.patch)
     * <p>
     * Returns -1 if version1 is older than version2
     * Returns 0 if version1 is equal to version2
     * Returns 1 if version1 is newer than version2
     */
    private static int compareVersions(String version1, String version2) {
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

    public CompletionStage<?> downloadFromGitHubAndUnzip(boolean dry) {
        if (state != UpdateState.SLEEPING) {
            String errMsg;
            switch (state) {
                case DOWNLOADING:
                    errMsg = "A JATOS update is already downloading.";
                    break;
                case DOWNLOADED:
                    errMsg = "A JATOS update was already downloaded.";
                    break;
                default:
                    errMsg = "Wrong update state";
            }
            CompletableFuture future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException(errMsg));
            return future;
        }

        if (dry) {
            state = UpdateState.DOWNLOADED;
            return CompletableFuture.completedFuture(null);
        }

        try {
//            String url =
//                    "https://github.com/JATOS/JATOS_examples/raw/master/examples/simple_example_jatos_v3.3.1.zip";
            String url = "https://github.com/JATOS/JATOS/releases/download/v" + latestJatosVersion
                    + "/jatos-" + latestJatosVersion + ".zip";
            String jatosZipFilename = "jatos-" + latestJatosVersionFull + ".zip";
            state = UpdateState.DOWNLOADING;
            CompletionStage<File> future = downloadAsync(url, jatosZipFilename);
            future.thenAccept(zipFile -> Errors.rethrow().get(() ->
                    ZipUtil.unzip(zipFile, tmpJatosDir.get()))
            );
            state = UpdateState.DOWNLOADED;
            scheduleUpdateStateReset();
            return future;
        } catch (Exception e) {
            state = UpdateState.SLEEPING;
            CompletableFuture future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private CompletionStage<File> downloadAsync(String url, String filename) throws IOException {
        File file = new File(IOUtils.TMP_DIR, filename);
        OutputStream outputStream = Files.newOutputStream(file.toPath());
        return ws.url(url).setMethod("GET").stream().thenCompose(res -> {
            Sink<ByteString, CompletionStage<Done>> outputWriter =
                    Sink.foreach(bytes -> outputStream.write(bytes.toArray()));
            return res.getBody().runWith(outputWriter, materializer)
                    .whenComplete((value, error) -> {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            LOGGER.error("Couldn't close stream after downloading " + url, e);
                        }
                    }).thenApply(v -> file);
        });
    }

    /**
     * One update once initialized (left state SLEEPING) can last max 1 hour. Then state is reset
     * back to SLEEPING.
     */
    private void scheduleUpdateStateReset() {
        actorSystem.scheduler().scheduleOnce(Duration.create(1, TimeUnit.HOURS),
                () -> state = UpdateState.SLEEPING,
                this.executionContext);
    }

    public void updateAndRestart(boolean dry) throws IOException {
        if (state == UpdateState.MOVING || state == UpdateState.RESTARTING) {
            return;
        }
        if (state != UpdateState.DOWNLOADED) {
            throw new IllegalStateException("Wrong update state");
        }
        if (!tmpJatosDir.get().isDirectory()) {
            state = UpdateState.SLEEPING;
            throw new IOException("JATOS update directory couldn't be found in " +
                    tmpJatosDir.get().getAbsolutePath());
        }

        state = UpdateState.MOVING;
        if (!dry && environment.isProd()) updateFiles();

        Logger.info("Restart JATOS to finish update to version " + latestJatosVersionFull);
        state = UpdateState.RESTARTING;
        if (environment.isProd()) restartJatos();
    }

    private void updateFiles() throws IOException {
        backupCurrentJatosFiles();
        Path srcUpdateDir = Files.list(tmpJatosDir.get().toPath()).findFirst()
                .orElseThrow(() -> new FileNotFoundException(
                        "JATOS update directory seems to be corrupted."));
        Path dstUpdateDir = Paths.get(Common.getBasepath(), "update-" + latestJatosVersionFull);
        copyDirContent(srcUpdateDir, dstUpdateDir);
        updateLoaderScripts(dstUpdateDir);
    }

    /**
     * Copies conf directory and loader scripts into a backup directory.
     */
    private static void backupCurrentJatosFiles() throws IOException {
        String bkpDirName = "backup_" + Common.getJatosVersion();
        Path bkpDir = Paths.get(Common.getBasepath(), bkpDirName);
        int i = 2;
        while (Files.exists(bkpDir)) {
            bkpDir = Paths.get(Common.getBasepath(), bkpDirName + "_" + i);
            i++;
        }
        Files.createDirectories(bkpDir);

        Files.copy(Paths.get(Common.getBasepath(), "conf"), bkpDir.resolve("conf"));
        Files.copy(Paths.get(Common.getBasepath(), "loader.sh"), bkpDir.resolve("loader.sh"));
        Files.copy(Paths.get(Common.getBasepath(), "loader.bat"), bkpDir.resolve("loader.bat"));
    }

    /**
     * Copies the contents of srcDir into the destDir directory. It does not overwrite existing
     * directories. It overwrites existing files. It copies file attributes.
     */
    private static void copyDirContent(Path srcDir, Path destDir) throws IOException {
        try (Stream<Path> stream = Files.walk(srcDir)) {
            // Use filter to not overwrite existing directories
            stream.filter(file -> !Files.isDirectory(file) ||
                    Files.notExists(destDir.resolve(srcDir.relativize(file))))
                    .forEach(sourcePath -> Errors.rethrow().get(() ->
                            Files.copy(sourcePath,
                                    destDir.resolve(srcDir.relativize(sourcePath)),
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.COPY_ATTRIBUTES)));
        }
    }

    private static void updateLoaderScripts(Path srcDir) throws IOException {
        Files.move(srcDir.resolve("loader.sh"),
                Paths.get(Common.getBasepath(), "loader.sh"), StandardCopyOption.REPLACE_EXISTING);
        Files.move(srcDir.resolve("loader.bat"),
                Paths.get(Common.getBasepath(), "loader.bat"), StandardCopyOption.REPLACE_EXISTING);
        Paths.get(Common.getBasepath(), "loader.sh").toFile().setExecutable(true);
        Paths.get(Common.getBasepath(), "loader.bat").toFile().setExecutable(true);
    }

    /**
     * Restarts JATOS (the Java application inclusive the JVM). Does not work during development
     * with sbt - only in production mode.
     * Used https://stackoverflow.com/questions/4159802
     */
    private void restartJatos() {
        // Execute the command in a shutdown hook, to be sure that all the
        // resources have been disposed before restarting the application
        applicationLifecycle.addStopHook(() -> {
            try {
                // Inherit the current process stdin / stdout / stderr (in Java called
                // System.in / System.out / System.err) to the newly started Process
                String[] cmd = getJatosCmdLine();
                LOGGER.info(String.join(" ", cmd));
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.inheritIO().start();
            } catch (IOException e) {
                Logger.error("Couldn't restart JATOS", e);
            }
            return CompletableFuture.completedFuture(null);
        });
        // First stop Play and then, to be sure, System.exit
        FutureConverters.toJava(Play.current().stop()).thenAccept((a) -> System.exit(0));
    }

    /**
     * Returns true if the OS JATOS is running on is either Linux, or Unix (MacOS) - and
     * false otherwise.
     */
    private static boolean isOsUx() {
        String osName = Common.getOsName().toLowerCase();
        return osName.contains("linux") || osName.contains("mac") || osName.contains("unix");
    }

    /**
     * Uses "/proc/self/cmdline" to get the command JATOS was started with (including all
     * parameters). Works only on Linux or Unix systems.
     */
    private static String[] getJatosCmdLine() {
        List<String> cmd = new ArrayList<>();

        // Get loader script with path and 'update' argument
        String loaderName = isOsUx() ? "loader.sh" : "loader.bat";
        String loader = Common.getBasepath() + File.separator + loaderName;
        cmd.add(loader);
        cmd.add("update");

        // Get command line arguments, like -Dhttp.address
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> args = runtimeMxBean.getInputArguments();
        cmd.addAll(args);
        // Remove arguments that are set anew with each start
        cmd.removeIf(a -> a.startsWith("-agentlib"));
        cmd.removeIf(a -> a.startsWith("-Dplay.crypto.secret"));
        cmd.removeIf(a -> a.startsWith("-Duser.dir"));
        cmd.removeIf(a -> a.startsWith("-Dconfig.file"));

        return cmd.toArray(new String[0]);
    }

}
