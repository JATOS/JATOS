package services.gui.importexport;

import exceptions.common.BadRequestException;
import exceptions.common.FieldValidationException;
import exceptions.common.ConflictException;
import general.common.Common;
import http.common.HttpUtils;
import models.gui.StagedStudySource;
import models.common.User;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.jgit.treewalk.TreeWalk;
import services.gui.importexport.GitCredentialsResolver.ResolvedCredentials;
import utils.common.IOUtils;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fetches Git study content and pushes selected properties and assets to branches. Reads and writes Git objects
 * directly: no checkout, hooks, submodules, LFS filters, or external Git executable. Studies using LFS for assets are
 * not currently supported. JGit performs the Git operations inside Java; users do not need Git installed.
 */
@Singleton
public class GitStudySourceIO {

    public record FetchedStudy(StagedStudySource content, String commitId) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    /**
     * Downloads a selected Git revision and prepares its study files in a temporary directory. It doesn’t create or
     * modify a JATOS study.
     */
    public FetchedStudy stage(String url, String ref, ResolvedCredentials credentials) throws IOException {
        Path tmpWorkDir = Files.createTempDirectory(IOUtils.tmpDir(), "study-git-");

        try {
            try (Repository repository = new IsolatedRepository(tmpWorkDir.resolve("repository.git"))) {
                repository.create(true);
                repository.getConfig().setString("http", null, "followRedirects", "false");
                repository.getConfig().setBoolean("http", null, "sslVerify", true);

                ObjectId object = fetchRevision(repository, url, ref, credentials);
                return extractStudySource(repository, object, tmpWorkDir, false);
            }
        } catch (IOException | RuntimeException e) {
            try {
                IOUtils.deleteRecursivelyIfExists(tmpWorkDir);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    /**
     * Holds a fetched branch and its temporary files until preview or push finishes.
     */
    public record PushDestination(Repository repository,
                                  String branch,
                                  FetchedStudy fetchedStudy) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            repository.close();
            fetchedStudy.close();
        }
    }

    /**
     * Holds the outcome after a push.
     */
    public record PushResult(String commitId, boolean changed) {}

    /**
     * Fetches an existing destination branch, rejecting tags and unresolved default branches.
     */
    public PushDestination preparePush(String url, String ref, ResolvedCredentials credentials) throws IOException {
        Path work = Files.createTempDirectory(IOUtils.tmpDir(), "study-git-");
        Repository repository = null;
        try {
            repository = new IsolatedRepository(work.resolve("repository.git"));
            repository.create(true);
            repository.getConfig().setString("http", null, "followRedirects", "false");
            repository.getConfig().setBoolean("http", null, "sslVerify", true);
            try (Transport transport = openTransport(repository, url.trim())) {
                transport.setCredentialsProvider(credentials);
                transport.setTimeout(Math.toIntExact(Common.getStudySourcesGitTimeout().getSeconds()));
                transport.setCheckFetchedObjects(true);
                try (FetchConnection connection = transport.openFetch()) {
                    Ref selected = selectRef(connection.getRefsMap(), ref);
                    // HEAD must identify a branch; matching commit IDs alone cannot identify the default branch.
                    Ref branch = selected.getLeaf();
                    if (!branch.getName().startsWith(Constants.R_HEADS))
                        throw new FieldValidationException("gitRef", "Git push requires a branch. Select a named branch if the default branch cannot be determined; tags support pulling only.");
                    connection.fetch(NullProgressMonitor.INSTANCE, List.of(branch), Set.of());
                    return new PushDestination(
                            repository,
                            branch.getName(),
                            extractStudySource(repository, branch.getObjectId(), work, true));
                }
            }
        } catch (IOException | RuntimeException e) {
            if (repository != null) repository.close();
            try {
                IOUtils.deleteRecursivelyIfExists(work);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            if (e instanceof TransportException)
                throw new BadRequestException("Could not fetch the Git destination. Check repository access, credentials, and the HTTPS connection.");
            throw e;
        }
    }

    /**
     * Replaces selected tree entries, creates a commit, and pushes only if the fetched branch is still current.
     */
    public PushResult push(PushDestination destination, String url, ResolvedCredentials credentials,
                           Path staged, boolean properties, boolean assets, User user) throws IOException {
        Repository repository = destination.repository();
        ObjectId base = ObjectId.fromString(destination.fetchedStudy().commitId());
        try (RevWalk walk = new RevWalk(repository);
             ObjectInserter inserter = repository.newObjectInserter()) {
            RevCommit parent = walk.parseCommit(base);
            ObjectId tree = replaceTree(repository, parent, inserter, staged, properties, assets);
            boolean changed = !tree.equals(parent.getTree().getId());
            ObjectId commit = base;
            if (changed) {
                String name = Common.getStudySourcesGitIdentityName();
                String email = Common.getStudySourcesGitIdentityEmail();
                if (Common.isStudySourcesGitIdentityUseUserDetails() && user != null) {
                    if (user.getName() != null && !user.getName().isBlank()) name = user.getName();
                    if (user.getEmail() != null && !user.getEmail().isBlank()) email = user.getEmail();
                }
                var identity = new PersonIdent(name, email);
                var commitBuilder = new CommitBuilder();
                commitBuilder.setTreeId(tree);
                commitBuilder.setParentId(base);
                commitBuilder.setAuthor(identity);
                commitBuilder.setCommitter(identity);
                commitBuilder.setMessage("Update study content");
                commit = inserter.insert(commitBuilder);
            }
            inserter.flush();
            pushCommit(repository, url, credentials, destination.branch(), base, commit);
            return new PushResult(commit.name(), changed);
        }
    }

    /**
     * Builds a Git tree from staged content while preserving all unselected file entries.
     */
    private ObjectId replaceTree(Repository repository, RevCommit parent, ObjectInserter inserter,
                                 Path staged, boolean properties, boolean assets) throws IOException {
        var entries = new ArrayList<DirCacheEntry>();
        // Preserve parent entries outside the properties/assets selected for replacement.
        try (TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(parent.getTree());
            tree.setRecursive(true);
            while (tree.next()) {
                String path = tree.getPathString();
                if (properties && !path.contains("/") && path.endsWith(".jas")) continue;
                if (assets && (path.equals("assets") || path.startsWith("assets/"))) continue;
                var entry = new DirCacheEntry(path);
                entry.setFileMode(tree.getFileMode(0));
                entry.setObjectId(tree.getObjectId(0));
                entries.add(entry);
            }
        }
        // Store the staged export as Git blobs and add its replacement entries.
        try (var files = Files.walk(staged)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                var entry = new DirCacheEntry(staged.relativize(file).toString().replace('\\', '/'));
                entry.setFileMode(FileMode.REGULAR_FILE);
                try (var input = Files.newInputStream(file)) {
                    entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, Files.size(file), input));
                }
                entries.add(entry);
            }
        }
        // DirCache requires Git's unsigned UTF-8 path ordering.
        entries.sort((a, b) -> java.util.Arrays.compareUnsigned(a.getRawPath(), b.getRawPath()));
        // Build the new snapshot through an in-memory index, without checking out files.
        DirCache cache = DirCache.newInCore();
        var builder = cache.builder();
        entries.forEach(builder::add);
        builder.finish();
        return cache.writeTree(inserter);
    }

    /**
     * Sends a normal branch update with the fetched commit as its expected old value.
     */
    private void pushCommit(Repository repository, String url, ResolvedCredentials credentials,
                            String branch, ObjectId base, ObjectId commit) throws IOException {
        try (Transport transport = openTransport(repository, url.trim())) {
            transport.setCredentialsProvider(credentials);
            transport.setTimeout(Math.toIntExact(Common.getStudySourcesGitTimeout().getSeconds()));
            var update = new RemoteRefUpdate(repository, null, commit, branch, false, null, base);
            transport.push(NullProgressMonitor.INSTANCE, List.of(update));
            switch (update.getStatus()) {
                case OK, UP_TO_DATE -> {}
                case REJECTED_REMOTE_CHANGED, REJECTED_NONFASTFORWARD ->
                        throw new ConflictException("The remote Git branch changed. Retry the push to fetch its latest content.");
                // Servers may report a race after advertisement as a generic rejection, just like a protected branch.
                case REJECTED_OTHER_REASON -> throw new ConflictException(
                        "Git push was rejected. The branch may have changed or be protected. Check write permissions and branch rules, then retry.");
                default ->
                        throw new BadRequestException("Git push was rejected. Check write permissions, credentials, and branch protection rules.");
            }
        } catch (TransportException e) {
            // Remote errors may contain credentials or private response bodies; do not return them to clients.
            throw new BadRequestException("Could not push to Git. Check write permissions, credentials, and the HTTPS connection. If the connection was lost, check the remote branch before retrying.");
        }
    }

    /**
     * Fetches the default branch or selected branch or tag and returns its object ID. Closes connections after
     * fetching. Error messages omit remote responses and authentication details.
     */
    private ObjectId fetchRevision(Repository repository, String url, String ref,
                                   ResolvedCredentials credentials) throws IOException {
        int timeout = Math.toIntExact(Common.getStudySourcesGitTimeout().getSeconds());
        try (Transport transport = openTransport(repository, url.trim())) {
            transport.setCredentialsProvider(credentials);
            transport.setTimeout(timeout);
            transport.setCheckFetchedObjects(true);
            try (FetchConnection connection = transport.openFetch()) {
                Ref selected = selectRef(connection.getRefsMap(), ref);
                connection.fetch(NullProgressMonitor.INSTANCE, List.of(selected), Set.of());
                return selected.getObjectId();
            }
        } catch (TransportException e) {
            if (timedOut(e))
                throw new BadRequestException("Git fetch timed out. Retry or increase the configured Git timeout.");
            throw new BadRequestException("Could not fetch the Git repository using " + credentials.source()
                    + " access. Check the repository URL, access permissions, credentials, and HTTPS connection. Redirects are not followed.");
        }
    }

    /**
     * Copies the fetched revision's .jas file and assets into temporary storage and returns its commit ID.
     */
    private FetchedStudy extractStudySource(Repository repository, ObjectId object, Path tmpWorkDir, boolean allowEmpty) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            var revision = walk.peel(walk.parseAny(object));
            if (!(revision instanceof RevCommit commit))
                throw new BadRequestException("The selected Git ref does not point to a commit.");

            Path snapshot = Files.createDirectory(tmpWorkDir.resolve("content"));
            Path properties = null;
            Path assets = null;
            try (TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(commit.getTree());

                while (tree.next()) {
                    String name = tree.getNameString();
                    if (name.endsWith(".jas")) {
                        if (properties != null)
                            throw new BadRequestException("The Git source contains more than one .jas file.");
                        properties = child(snapshot, name);
                        copyBlob(repository, tree, properties);
                    } else if (name.equals("assets")) {
                        if (!tree.getFileMode(0).equals(FileMode.TREE))
                            throw new BadRequestException("Git source assets must be a directory, not a link or submodule.");
                        assets = Files.createDirectory(snapshot.resolve("assets"));
                        copyTree(repository, tree.getObjectId(0), assets);
                    }
                }
            }
            if (!allowEmpty && properties == null && assets == null)
                throw new BadRequestException("The Git source must contain a .jas file or an assets/ directory, or both.");
            return new FetchedStudy(new StagedStudySource(tmpWorkDir, properties, assets), commit.name());
        }
    }

    /**
     * Creates an HTTPS transport restricted to the repository's origin. The caller sets credentials and timeout and
     * closes the transport. Tests may use local repositories.
     */
    protected Transport openTransport(Repository repository, String url) throws IOException {
        HttpUtils.getGitOriginFromRepositoryUrl(url);
        try {
            Transport transport = Transport.open(repository, new URIish(url));
            if (transport instanceof TransportHttp http)
                http.setHttpConnectionFactory(
                        new OriginConnectionFactory(
                                HttpUtils.getGitOriginFromRepositoryUrl(url),
                                new JDKHttpConnectionFactory()));
            return transport;
        } catch (URISyntaxException e) {
            throw new IOException("Invalid Git repository URL.");
        }
    }

    /**
     * Allows HTTP connections only to the repository's HTTPS origin (scheme, host, and port). JGit may discover
     * additional download URLs while fetching objects, so checking the initial repository URL alone is insufficient.
     * Checks each URL before delegating connection creation, preventing authentication headers or cookies from being
     * sent to another origin.
     */
    static final class OriginConnectionFactory implements HttpConnectionFactory {
        private final String origin;
        private final HttpConnectionFactory delegate;

        OriginConnectionFactory(String origin, HttpConnectionFactory delegate) {
            this.origin = origin;
            this.delegate = delegate;
        }

        private void check(URL url) throws IOException {
            String destination;
            try {
                destination = HttpUtils.normalizeGitOrigin(url.getProtocol() + "://" + url.getAuthority());
            } catch (IllegalArgumentException e) {
                throw new IOException("Git requests must use the configured HTTPS origin.");
            }
            if (!origin.equals(destination))
                throw new IOException("Git requests must use the configured HTTPS origin.");
        }

        @Override
        public HttpConnection create(URL url) throws IOException {
            check(url);
            return delegate.create(url);
        }

        @Override
        public HttpConnection create(URL url, Proxy proxy) throws IOException {
            check(url);
            return delegate.create(url, proxy);
        }
    }

    /**
     * Selects the advertised default branch, named branch, or tag; rejects missing or ambiguous names.
     */
    private Ref selectRef(Map<String, Ref> refs, String value) {
        String name = value == null ? "" : value.trim();
        Ref selected;
        if (name.isEmpty()) {
            selected = refs.get(Constants.HEAD);
        } else if (name.startsWith(Constants.R_HEADS) || name.startsWith(Constants.R_TAGS)) {
            selected = refs.get(name);
        } else {
            Ref branch = refs.get(Constants.R_HEADS + name);
            Ref tag = refs.get(Constants.R_TAGS + name);
            if (branch != null && tag != null)
                throw new FieldValidationException("gitRef", "The name matches both a branch and a tag. Use refs/heads/ or refs/tags/ to select one.");
            selected = branch != null ? branch : tag;
        }
        if (selected == null || selected.getObjectId() == null)
            throw new FieldValidationException("gitRef", "The requested branch or tag does not exist, or the repository has no default branch commit.");
        return selected;
    }

    /**
     * Recursively extracts a Git directory, skipping .git entries and checking each destination filename.
     */
    private void copyTree(Repository repository, ObjectId id, Path destination) throws IOException {
        try (TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(id);
            while (tree.next()) {
                String name = tree.getNameString();
                if (name.equalsIgnoreCase(".git")) continue;
                Path target = child(destination, name);
                if (tree.getFileMode(0).equals(FileMode.TREE)) {
                    Files.createDirectory(target);
                    copyTree(repository, tree.getObjectId(0), target);
                } else copyBlob(repository, tree, target);
            }
        }
    }

    /**
     * Resolves a single filename within its parent directory, rejecting unsafe names or paths that escape it.
     */
    private Path child(Path parent, String name) {
        Path target = parent.resolve(name).normalize();
        if (name.isEmpty()
                || name.equals(".")
                || name.equals("..")
                || name.contains("\\")
                || name.contains(":")
                || !parent.equals(target.getParent()))
            throw new BadRequestException("The Git source contains an unsafe filename.");
        return target;
    }

    /**
     * Writes a regular Git file to a new destination file; rejects symlinks, submodules, and other entry types.
     */
    private void copyBlob(Repository repository, TreeWalk tree, Path destination) throws IOException {
        FileMode mode = tree.getFileMode(0);
        if (!mode.equals(FileMode.REGULAR_FILE) && !mode.equals(FileMode.EXECUTABLE_FILE))
            throw new BadRequestException("Git source content must contain regular files and directories, without links or submodules.");
        try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            repository.open(tree.getObjectId(0), Constants.OBJ_BLOB).copyTo(output);
        }
    }

    /**
     * Checks whether an exception was caused by a timeout.
     */
    private boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof InterruptedIOException) return true;
        return false;
    }

    /**
     * Uses only the temporary repository's configuration, without inheriting global Git settings from the computer or
     * container running JATOS (e.g., ~/.gitconfig of the operating-system user running JATOS). This prevents host
     * authentication headers, cookies, redirect rules, or TLS settings from affecting JATOS requests. Credentials and
     * connection settings are supplied explicitly by JATOS.
     */
    private static final class IsolatedRepository extends FileRepository {
        private FileBasedConfig isolated;

        IsolatedRepository(Path directory) throws IOException {
            super(new FileRepositoryBuilder().setGitDir(directory.toFile()).setBare().setup());
        }

        @Override
        public FileBasedConfig getConfig() {
            if (isolated == null)
                isolated = new FileBasedConfig(getDirectory().toPath().resolve("config").toFile(), getFS());
            return isolated;
        }
    }
}
