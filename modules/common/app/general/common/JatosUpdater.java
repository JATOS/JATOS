package general.common;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.SystemUtils;
import play.Environment;
import play.Logger;
import play.api.Play;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
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
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class handles JATOS updates
 * <p>
 * Some hints:
 * - JATOS releases are currently stored at GitHub
 * - The data requested from GitHub are stored in UpdateInfo
 * - If there is an 'n' in the release's version an update is forbidden. This is safety feature in case an future update
 * is not compatible with this update process.
 * - The new release might need a new Java version. The release's Java version is determined by the asset's filename
 * (see newJavaVersion).
 * - The current state of the update process (UpdateState) is stored in 'state'.
 * - The update process is finished in the loader script
 * - In the GUI the update is handled in the home view
 * <p>
 * Update process:
 * 1. check GitHub for a new releases and put release data into UpdateInfo
 * 2. ask user (GUI home view)
 * 3. download and unzip new release into system's tmp folder
 * 4. ask user (GUI home view)
 * 5. move new release into a separate folder within the current JATOS installation folder
 * 6. exchange loader scripts and config folder
 * 7. restart JATOS: finish process with an stop hook that runs the new loader script with an 'update' parameter
 * 8. The loader script moves everything in the new release folder into the JATOS installation folder (eventual
 * overwriting existing files)
 * 9. The loader script starts JATOS again
 * 10. JATOS shows a success msg (or a failure msg)
 *
 * @author Kristian Lange (2019)
 */
@Singleton
public class JatosUpdater {

	private static final Logger.ALogger LOGGER = Logger.of(JatosUpdater.class);

	private enum UpdateState {
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
	private UpdateState state = UpdateState.SLEEPING;

	/**
	 * Last time the latest release info was requested from GitHub
	 */
	private LocalTime lastTimeAskedReleaseInfo;

	private ReleaseInfo currentReleaseInfo;

	/**
	 * Contains all info about an JATOS update. It's also send as JSON to the GUI.
	 */
	@SuppressWarnings("WeakerAccess")
	// Fields have to be public for JSON serialization.
	class ReleaseInfo {

		/**
		 * Version of the currently installed one in format x.x.x
		 */
		public final String currentVersion;

		/**
		 * Latest version of JATOS like in GitHub
		 */
		public final String latestVersionFull;

		/**
		 * Latest version of JATOS in format x.x.x
		 */
		public final String latestVersion;

		/**
		 * Is it a pre-release
		 */
		public final boolean isPrerelease;

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
		 * Is the version of the latest release a newer one than the currently installed
		 */
		public final boolean isNewerVersion;

		/**
		 * Versions with an 'n' in the name are not allowed to be updated automatically. This is a
		 * safety switch if an future update isn't compatible with this way of update.
		 * Additionally JATOS on Windows doesn't allow automatic updates.
		 */
		public boolean isUpdateAllowed;

		/**
		 * Java version needed for the release. It's determined from the asset's filename: everything between 'java'
		 * and '.zip', e.g. 'jatos-3.3.6_linux_java1.8.zip' -> '1.8'
		 */
		public String newJavaVersion;

		/**
		 * If newJavaVersion is a different from the currently installed one, it's automatically a newer Java version.
		 */
		public boolean isNewerJava;

		ReleaseInfo(JsonNode jsonNode) {
			latestVersionFull = jsonNode.get("tag_name").asText();
			latestVersion = latestVersionFull.replaceAll("[^\\d.]", "");
			isPrerelease = jsonNode.get("prerelease").asBoolean();
			releaseNotes = jsonNode.get("body").asText();
			isNewerVersion = compareVersions(latestVersion, Common.getJatosVersion()) == 1;
			isUpdateAllowed = !latestVersionFull.contains("n") && isOsUx();
			currentVersion = Common.getJatosVersion();
			jsonNode.get("assets").forEach(this::getFieldsFromAsset);
		}

		/**
		 * Compare two JATOS versions (major.minor.patch)
		 * <p>
		 * Returns -1 if version1 is older than version2
		 * Returns 0 if version1 is equal to version2
		 * Returns 1 if version1 is newer than version2
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
			if (!filename.contains(".zip")) return;

			if ((SystemUtils.IS_OS_LINUX && filename.contains("linux")) ||
					(SystemUtils.IS_OS_MAC && filename.contains("mac")) ||
					(SystemUtils.IS_OS_WINDOWS && filename.contains("win"))) {
				zipJavaUrl = asset.get("browser_download_url").asText();
				zipJavaSize = asset.get("size").asInt();
				newJavaVersion = getAssetsJavaVersion(filename);
				isNewerJava = !newJavaVersion.equals(System.getProperty("java.specification.version"));
			} else if (!filename.contains("linux") && !filename.contains("mac") && !filename.contains("win")) {
				zipUrl = asset.get("browser_download_url").asText();
				zipSize = asset.get("size").asInt();
			}
		}

		private String getAssetsJavaVersion(String filename) {
			Pattern p = Pattern.compile("java(.+).zip"); // Everything between 'java' and '.zip' is Java version
			Matcher m = p.matcher(filename);
			return m.find() ? m.group(1) : "1.8"; // Default Java is 1.8
		}
	}

	/**
	 * Determine the path and name of the directory where the update files will be stored.
	 */
	private final Supplier<File> tmpJatosDir = () -> new File(IOUtils.TMP_DIR,
			"jatos-" + currentReleaseInfo.latestVersionFull);

	private final WSClient ws;

	private final Materializer materializer;

	private final ActorSystem actorSystem;

	private final ExecutionContext executionContext;

	private final ApplicationLifecycle applicationLifecycle;

	private final Environment environment;

	@Inject
	JatosUpdater(WSClient ws, Materializer materializer, ActorSystem actorSystem,
			ExecutionContext executionContext, ApplicationLifecycle applicationLifecycle, Environment environment) {
		this.ws = ws;
		this.materializer = materializer;
		this.actorSystem = actorSystem;
		this.executionContext = executionContext;
		this.applicationLifecycle = applicationLifecycle;
		this.environment = environment;
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

	public CompletionStage<JsonNode> getReleaseInfo(boolean allowPreReleases) {
		return getLatestReleaseInfo(allowPreReleases).thenApply(releaseInfo -> {
			currentReleaseInfo = releaseInfo;
			ObjectNode json = (ObjectNode) Json.toJson(currentReleaseInfo);
			json.put("currentUpdateState", state.toString());
			resetUpdateState();
			return json;
		});
	}

	/**
	 * Gets the latest JATOS release information from GitHub and returns it as a String in format
	 * x.x.x. To prevent high load on GitHub it stores it locally and newly requests it only once
	 * per hour (only if allowPreReleases is false).
	 *
	 * @param allowPreReleases If true it includes pre-releases.
	 */
	private CompletionStage<ReleaseInfo> getLatestReleaseInfo(boolean allowPreReleases) {
		boolean notOlderThanAnHour =
				lastTimeAskedReleaseInfo != null && LocalTime.now().minusHours(1).isBefore(lastTimeAskedReleaseInfo);
		if (currentReleaseInfo != null && notOlderThanAnHour && !allowPreReleases && !currentReleaseInfo.isPrerelease) {
			return CompletableFuture.completedFuture(currentReleaseInfo);
		}
		return allowPreReleases ? requestLatestReleaseInfoInclPre() : requestLatestReleaseInfo();
	}

	private CompletionStage<ReleaseInfo> requestLatestReleaseInfo() {
		String url = "https://api.github.com/repos/JATOS/JATOS/releases/latest";
		return ws.url(url).setRequestTimeout(Duration.ofHours(1)).get().thenApply(res -> {
			JsonNode json = res.asJson();
			ReleaseInfo releaseInfo = new ReleaseInfo(json);
			LOGGER.info("Checked GitHub for latest release of JATOS: " + releaseInfo.latestVersionFull);
			lastTimeAskedReleaseInfo = LocalTime.now();
			return releaseInfo;
		});
	}

	private CompletionStage<ReleaseInfo> requestLatestReleaseInfoInclPre() {
		String url = "https://api.github.com/repos/JATOS/JATOS/releases";
		return ws.url(url).setRequestTimeout(Duration.ofHours(1)).get().thenApply(res -> {
			JsonNode first = res.asJson().get(0);
			ReleaseInfo releaseInfo = new ReleaseInfo(first);
			String msg = "Checked GitHub for latest release of JATOS (allowing pre-release): "
					+ releaseInfo.latestVersionFull;
			if (releaseInfo.isPrerelease) msg += " (pre-release)";
			LOGGER.info(msg);
			lastTimeAskedReleaseInfo = LocalTime.now();
			return releaseInfo;
		});
	}

	public CompletionStage<?> downloadFromGitHubAndUnzip(boolean dry) {
		if (!currentReleaseInfo.isUpdateAllowed) {
			CompletableFuture future = new CompletableFuture<>();
			future.completeExceptionally(new IllegalStateException("Can't update to version "
					+ currentReleaseInfo.latestVersionFull
					+ " automatically. This JATOS release has to be updated manually."));
			return future;
		}
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
			LOGGER.info("Dry download");
			return CompletableFuture.completedFuture(null);
		}

		try {
			state = UpdateState.DOWNLOADING;
			String url = currentReleaseInfo.isNewerJava ? currentReleaseInfo.zipJavaUrl : currentReleaseInfo.zipUrl;
			String downloadFilename = "jatos-" + currentReleaseInfo.latestVersionFull + ".zip";
			CompletionStage<File> future = downloadAsync(url, downloadFilename);
			future.thenAccept(zipFile -> Errors.rethrow().get(() -> {
				File file = ZipUtil.unzip(zipFile, tmpJatosDir.get());
				state = UpdateState.DOWNLOADED;
				scheduleStateReset();
				LOGGER.info("Downloaded and unzipped new JATOS " + downloadFilename);
				return file;
			}));
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
		LOGGER.info("Download " + url);
		CompletionStage<WSResponse> futureResponse = ws.url(url).setMethod("GET").stream();
		return futureResponse.thenCompose(res -> {
			Source<ByteString, ?> responseBody = res.getBodyAsSource();
			Sink<ByteString, CompletionStage<Done>> outputWriter =
					Sink.foreach(bytes -> outputStream.write(bytes.toArray()));
			return responseBody.runWith(outputWriter, materializer).whenComplete((value, error) -> {
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
	private void scheduleStateReset() {
		actorSystem.scheduler()
				.scheduleOnce(Duration.ofHours(1), this::resetState, this.executionContext);
	}

	private void resetState() {
		state = UpdateState.SLEEPING;
	}

	/**
	 * Backups, updates files and restarts JATOS
	 *
	 * @param backupAll If true, everything in the JATOS directory will be copied into a backup folder.
	 *                  If false, only the conf directory and the loader scripts.
	 */
	public void updateAndRestart(boolean backupAll) throws IOException {
		if (state == UpdateState.MOVING || state == UpdateState.RESTARTING) {
			return;
		}
		if (!currentReleaseInfo.isUpdateAllowed) {
			throw new IllegalStateException("Can't update to version " + currentReleaseInfo.latestVersionFull
					+ " automatically. This JATOS release has to be updated manually.");
		}
		if (state != UpdateState.DOWNLOADED) {
			throw new IllegalStateException("Wrong update state (" + state + ")");
		}
		if (!tmpJatosDir.get().isDirectory()) {
			state = UpdateState.SLEEPING;
			throw new IOException("JATOS update directory couldn't be found in " + tmpJatosDir.get().getAbsolutePath());
		}

		if (!environment.isProd()) {
			return;
		}

		// Execute backup, update and restart in a shutdown hook. This ensures that all resources have been closed
		// beforehand (e.g. database closed and no more changing of study assets). It's especially important that the
		// H2 doesn't write into it's files anymore, otherwise they might get corrupted.
		applicationLifecycle.addStopHook(() -> {
			state = UpdateState.MOVING;
			backupCurrentJatosFiles(backupAll);
			updateFiles();

			try {
				state = UpdateState.RESTARTING;
				// Inherit the current process stdin / stdout / stderr (in Java called
				// System.in / System.out / System.err) to the newly started Process
				// Used https://stackoverflow.com/questions/4159802
				String[] cmd = getJatosCmdLine();
				LOGGER.info(String.join(" ", cmd));
				ProcessBuilder pb = new ProcessBuilder(cmd);
				pb.inheritIO().start();
			} catch (IOException e) {
				LOGGER.error("Couldn't restart JATOS", e);
			}

			return CompletableFuture.completedFuture(null);
		});

		LOGGER.info("Restart JATOS to finish update to version " + currentReleaseInfo.latestVersionFull);
		// First stop Play and then, to be sure, System.exit
		FutureConverters.toJava(Play.current().stop()).thenAccept((a) -> System.exit(0));
	}

	/**
	 * Makes backups of current JATOS files. Depending on configOnly copies only loader scripts and config/ -
	 * or everything.
	 */
	private void backupCurrentJatosFiles(boolean backupAll) throws IOException {
		String bkpDirName = "backup_" + Common.getJatosVersion();
		Path bkpDir = Paths.get(Common.getBasepath(), bkpDirName);
		int i = 2;
		while (Files.exists(bkpDir)) {
			bkpDir = Paths.get(Common.getBasepath(), bkpDirName + "_" + i);
			i++;
		}
		Files.createDirectories(bkpDir);

		if (backupAll) {
			IOFileFilter filter = FileFilterUtils.notFileFilter(FileFilterUtils
					.or(FileFilterUtils.nameFileFilter(bkpDir.getFileName().toString()),
							FileFilterUtils.nameFileFilter("RUNNING_PID")));
			FileUtils.copyDirectory(new File(Common.getBasepath()), bkpDir.toFile(), filter);
			LOGGER.info("Backup of current JATOS files into " + bkpDir.getFileName());
		} else {
			FileUtils.copyDirectory(FileUtils.getFile(Common.getBasepath(), "conf"), bkpDir.resolve("conf").toFile());
			Files.copy(Paths.get(Common.getBasepath(), "loader.sh"), bkpDir.resolve("loader.sh"));
			// JATOS Docker has no loader.bat
			if (Files.exists(Paths.get(Common.getBasepath(), "loader.bat"))) {
				Files.copy(Paths.get(Common.getBasepath(), "loader.bat"), bkpDir.resolve("loader.bat"));
			}
			LOGGER.info("Backup of current version of loader scripts and conf/ into " + bkpDir.getFileName());
		}
	}

	private void updateFiles() throws IOException {
		Path srcUpdateDir = Files.list(tmpJatosDir.get().toPath()).findFirst()
				.orElseThrow(() -> new FileNotFoundException("JATOS update directory seems to be corrupted."));
		Path dstUpdateDir = Paths.get(Common.getBasepath(), "update-" + currentReleaseInfo.latestVersionFull);
		if (Files.exists(dstUpdateDir)) {
			Files.delete(dstUpdateDir);
			LOGGER.info("Deleted old update directory " + dstUpdateDir);
		}
		FileUtils.copyDirectory(srcUpdateDir.toFile(), dstUpdateDir.toFile());
		LOGGER.info("Copied JATOS update files into JATOS installation folder under " + dstUpdateDir);

		updateLoaderScripts(dstUpdateDir);

		FileUtils.deleteDirectory(tmpJatosDir.get());
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static void updateLoaderScripts(Path srcDir) throws IOException {
		Files.move(srcDir.resolve("loader.sh"), Paths.get(Common.getBasepath(), "loader.sh"),
				StandardCopyOption.REPLACE_EXISTING);
		Files.move(srcDir.resolve("loader.bat"), Paths.get(Common.getBasepath(), "loader.bat"),
				StandardCopyOption.REPLACE_EXISTING);
		Paths.get(Common.getBasepath(), "loader.sh").toFile().setExecutable(true);
		Paths.get(Common.getBasepath(), "loader.bat").toFile().setExecutable(true);
		LOGGER.info("Replaced loader scripts with newer version.");
	}

	/**
	 * Returns true if the OS JATOS is running on is either Linux, or Unix (MacOS) - and
	 * false otherwise.
	 */
	private static boolean isOsUx() {
		return SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_UNIX;
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
		cmd.removeIf(a -> a.startsWith("-Dplay.crypto.secret")); // old secret config key
		cmd.removeIf(a -> a.startsWith("-Dplay.http.secret.key")); // new secret config key
		cmd.removeIf(a -> a.startsWith("-Duser.dir"));
		cmd.removeIf(a -> a.startsWith("-Dconfig.file")); // JATOS config file (will be added again by loader script)
		cmd.removeIf(a -> a.startsWith("-DJATOS_UPDATE_MSG")); // Msgs from a prior update

		return cmd.toArray(new String[0]);
	}

}
