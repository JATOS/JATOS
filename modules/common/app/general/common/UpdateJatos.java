package general.common;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import play.Logger;
import play.libs.ws.WSClient;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * This class handles JATOS updates. It works only on Linux or MacOS. JATOS can be updated by the
 * GUI. First it checks whether there is a new version available, then it downloads it (if the user
 * is admin), then it updates files, and finally does a restart. JATOS releases are stored at
 * GitHub. For download and file update it uses an external Shell script.
 *
 * @author Kristian Lange (2019)
 */
@Singleton
public class UpdateJatos {

    private enum UpdateState {WAITING, UPDATABLE, DOWNLOADING, DOWNLOADED, MOVING, MOVED, RESTARTING}

    private UpdateState state = UpdateState.WAITING;

    /**
     * Latest version (no draft, no prerelease) of JATOS in format x.x.x
     */
    private String latestJatosVersion;

    /**
     * Last time the latest version info was requested from GitHub
     */
    private LocalTime lastTimeAskedVersion;

    private final WSClient ws;

    @Inject
    UpdateJatos(WSClient ws) {
        this.ws = ws;
    }

    public UpdateState getState() {
        return state;
    }

    /**
     * Gets the latest JATOS version information from GitHub (no draft, no prerelease) and returns
     * it as a String in format x.x.x. To prevent high load on GitHub it stores it locally and newly
     * requests it only once per day.
     */
    public CompletionStage<String> checkUpdatable() {
        boolean notOlderThanADay = lastTimeAskedVersion != null &&
                LocalTime.now().minusHours(24).isBefore(lastTimeAskedVersion);
        if (!Strings.isNullOrEmpty(latestJatosVersion) && notOlderThanADay) {
            return CompletableFuture.completedFuture(latestJatosVersion);
        }

        // GitHub API endpoint to get info about the latest Release. Draft releases and prereleases
        // are not returned by this endpoint.
        String url = "https://api.github.com/repos/JATOS/JATOS/releases/latest";
        return ws.url(url).setRequestTimeout(60000).get().thenApply(res -> {
            latestJatosVersion = res.asJson().findPath("tag_name").asText().replace("v", "");
            lastTimeAskedVersion = LocalTime.now();

            if (isUpdatable(latestJatosVersion) && state == UpdateState.WAITING) {
                state = UpdateState.UPDATABLE;
            }
            Logger.info("Checked GitHub for latest version of JATOS: " + latestJatosVersion);
            return isUpdatable(latestJatosVersion) ? latestJatosVersion : "none";
        });
    }

    private boolean isUpdatable(String latestJatosVersion) {
        int currentVersionNumber =
                Integer.parseInt(Common.getJatosVersion().replaceAll("\\.", ""));
        int latestVersionNumber = Integer.parseInt(latestJatosVersion.replaceAll("\\.", ""));
        return currentVersionNumber < latestVersionNumber;
//        if (currentVersionNumber < latestVersionNumber && state == UpdateState.WAITING) {
//            state = UpdateState.UPDATABLE;
//        }
    }

    /**
     * Uses an external Shell script to download the latest JATOS version into tmp directory
     */
    public void download() throws IOException, InterruptedException {
        Logger.info("Download JATOS update " + latestJatosVersion + ". Run external script.");
        runUpdateScript("download", latestJatosVersion);
    }

    /**
     * Uses an external Shell script to move JATOS update files into the current JATOS working dir
     */
    public void updateFiles() throws IOException, InterruptedException {
        Logger.info("Move update files into JATOS' working directory. Run external script.");
        runUpdateScript("updateFiles");
    }

    private void runUpdateScript(String... parameters) throws IOException, InterruptedException {
        List<String> cmd = Lists.asList(Common.getBasepath() + "/update.sh", parameters);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(Common.getBasepath()));
        Process p = pb.start();
        Logger.info("update.sh: " + org.apache.commons.io.IOUtils.toString(p.getInputStream()));
        Logger.error("update.sh: " + org.apache.commons.io.IOUtils.toString(p.getErrorStream()));
        p.waitFor();
        if (p.exitValue() != 0) throw new IOException();
    }

    /**
     * Restarts JATOS (the Java application inclusive the JVM). Does not work during development
     * with sbt - only in production mode.
     * Used https://stackoverflow.com/questions/4159802
     */
    public void restartJatos() {
        // Execute the command in a shutdown hook, to be sure that all the
        // resources have been disposed before restarting the application
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Inherit the current process stdin / stdout / stderr (in Java called
                // System.in / System.out / System.err) to the newly started Process
                new ProcessBuilder(getJatosCmdLine()).inheritIO().start();
            } catch (IOException e) {
                Logger.error("Couldn't restart JATOS", e);
            }
        }));

        Logger.info("Restart JATOS to finish update to version " + latestJatosVersion);
        System.exit(0);
    }

    /**
     * Returns true if the OS JATOS is running on is either Linux or MacOS - and false otherwise.
     */
    public static boolean isOsUx() {
        String osName = Common.getOsName().toLowerCase();
        return !(osName.contains("linux") || osName.contains("mac"));
    }

    /**
     * Uses "/proc/self/cmdline" to get the command JATOS was started with (including all
     * parameters). Works only on Linux or Unix systems.
     */
    private static String[] getJatosCmdLine() throws IOException {
        return IOUtils.readFirstLine("/proc/self/cmdline").split("\u0000");
    }

}
