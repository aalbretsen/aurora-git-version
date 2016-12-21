package ske.aurora.gitversion;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitVersion {

    public static class Options {
        public String versionPrefix = "v";
        public boolean fallbackToBranchNameEnv = true;
        public String fallbackVersion = "unknown";
        public String fallbackBranchNameEnvName = "BRANCH_NAME";
        public String versionFromBranchNamePostfix = "-SNAPSHOT";
    }

    private final Options options;

    private final Repository repository;

    public static String determineVersion(File gitDir) throws IOException {
        return determineVersion(gitDir, new Options());
    }

    /**
     * Determines a version (typically for an application or software library) by inspecting the current state of
     * the Git metadata.
     * <p>
     * The rules are as follows;
     * <ol>
     * <li>If HEAD is at a tag and the name of that tag starts with <code>options.versionPrefix</code>, use the
     * name of that tag with the prefix removed as the version name.</li>
     * <li>If HEAD is not at a tag, and we are not in detached HEAD state, use the name of the current branch
     * concatenated with <code>options.versionFromBranchNamePostfix</code> as the version name</li>
     * <li>If HEAD is not at a tag, and we are in detached HEAD state, first look for the presence of an environment
     * variable with the name <code>fallbackBranchNameEnvName</code> and use that concatenated with
     * <code>options.versionFromBranchNamePostfix</code> as the version name</li>
     * <li>If the environment variable does not exist, search for a branch with the commit in and use that concatenated
     * with <code>options.versionFromBranchNamePostfix</code> as the version name. The first branch with the commit in
     * it will be used.</li>
     * </ol>
     *
     * @param gitDir
     * @param options
     * @return
     * @throws IOException
     */
    public static String determineVersion(File gitDir, Options options) throws IOException {
        return new GitVersion(gitDir, options).determineVersion();
    }

    protected GitVersion(File gitDir, Options options) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        this.repository = builder.setGitDir(new File(gitDir, ".git"))
            .readEnvironment() // scan environment GIT_* variables
            .setMustExist(true)
            .build();
        this.options = options;
    }

    protected String determineVersion() throws IOException {
        ObjectId head = repository.resolve("HEAD");
        Optional<String> currentBranchName = getBranchName(head);

        Optional<String> versionTagOnHead = getVersionTagOnCommit(head);

        return versionTagOnHead
            .map(this::getVersionFromVersionTag)
            .orElseGet(() -> currentBranchName
                .map(this::getVersionFromBranchName)
                .orElse(options.fallbackVersion));
    }

    protected String getVersionFromVersionTag(String versionTag) {

        return versionTag.replaceFirst(options.versionPrefix, "");
    }

    protected String getVersionFromBranchName(String currentBranchName) {

        String versionSafeName = currentBranchName.replaceAll("[\\/-]", "_");
        return String.format("%s%s", versionSafeName, options.versionFromBranchNamePostfix);
    }

    protected Optional<String> getVersionTagOnCommit(ObjectId commit) {

        return repository.getTags().entrySet().stream()
            .filter(entry -> entry.getValue().getObjectId().equals(commit))
            .filter(entry -> entry.getKey().startsWith(options.versionPrefix))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    protected Optional<String> getBranchName(ObjectId commitId) throws IOException {

        String currentBranchName = repository.getBranch();

        boolean isDetachedHead = commitId.getName().equals(currentBranchName);
        if (!isDetachedHead) {
            return Optional.of(currentBranchName);
        }

        return getBranchNameFromDetachedHead(commitId);
    }

    /**
     * If we are trying to determine the branch name of the current commit when we are in detached head
     * state, we need to resort to either hints or heuristics. This method will first check for the presence of
     * an environment variable called <code>options.fallbackBranchNameEnvName</code> (default BRANCH_NAME). If it
     * exists, its value will be used as branch name (Jenkins sets this environment variable before performing a
     * build).
     * <p>
     * If the environment variable is not set we have to resort to a broad search for the commit. We pick the first
     * branch we find the commit in.
     *
     * @param commitId
     * @return
     * @throws IOException
     */
    protected Optional<String> getBranchNameFromDetachedHead(ObjectId commitId) throws IOException {

        if (options.fallbackToBranchNameEnv) {
            String branchNameFromEnv = System.getenv(options.fallbackBranchNameEnvName);
            if (branchNameFromEnv != null) {
                return Optional.of(branchNameFromEnv);
            }
        }

        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(repository.resolve(commitId.getName() + "^0"));
        return repository.getAllRefs().entrySet().stream()
            .filter(e -> e.getKey().startsWith(Constants.R_HEADS))
            .filter(e -> {
                try {
                    return walk.isMergedInto(commit, walk.parseCommit(e.getValue().getObjectId()));
                } catch (IOException e1) {
                    return false;
                }
            })
            .map(e -> e.getValue().getName().replaceFirst("refs/heads/", ""))
            .findFirst();
    }
}