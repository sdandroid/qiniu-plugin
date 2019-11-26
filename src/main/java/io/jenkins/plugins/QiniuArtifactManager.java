package io.jenkins.plugins;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.model.StandardArtifactManager;
import jenkins.util.VirtualFile;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QiniuArtifactManager extends ArtifactManager {
    private static final Logger LOG = Logger.getLogger(QiniuArtifactManager.class.getName());

    private final String accessKey, secretKey, bucketName, objectNamePrefix, downloadDomain;
    private final boolean useHTTPs, infrequentStorage;
    private String objectNamePrefixWithBuildNumber;
    private StandardArtifactManager standardArtifactManager = null;

    public QiniuArtifactManager(@Nonnull Run<?, ?> run, @Nonnull String accessKey, @Nonnull String secretKey,
                                @Nonnull String bucketName, @Nonnull String objectNamePrefix,
                                @Nonnull String downloadDomain, boolean useHTTPs, boolean infrequentStorage) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectNamePrefix = objectNamePrefix;
        this.downloadDomain = downloadDomain;
        this.useHTTPs = useHTTPs;
        this.infrequentStorage = infrequentStorage;
        this.onLoad(run);
    }

    @Override
    public void onLoad(@Nonnull Run<?, ?> run) {
        if (run.getParent() instanceof AbstractProject) {
            if (QiniuArtifactManagerFactory.isQiniuPublisherConfigured((AbstractProject) run.getParent())) {
                this.objectNamePrefixWithBuildNumber = this.objectNamePrefix;
                if (!this.objectNamePrefixWithBuildNumber.isEmpty() && !this.objectNamePrefixWithBuildNumber.endsWith(File.separator)) {
                    this.objectNamePrefixWithBuildNumber += File.separator;
                }
                this.objectNamePrefixWithBuildNumber += run.getParent().getFullName();
                this.objectNamePrefixWithBuildNumber += File.separator;
                this.objectNamePrefixWithBuildNumber += run.getId();
                this.objectNamePrefixWithBuildNumber += File.separator;
                LOG.log(Level.INFO, "QiniuArtifactManager was loaded for Qiniu, prefix: {0}", this.objectNamePrefixWithBuildNumber);
                return;
            }
        }
        LOG.log(Level.INFO, "QiniuArtifactManager was loaded for StandardArtifactManager");
        this.standardArtifactManager = new StandardArtifactManager(run);
    }

    @Override
    public void archive(FilePath workspace, Launcher launcher, BuildListener buildListener, Map<String, String> pathMap) throws IOException, InterruptedException {
        if (this.standardArtifactManager != null) {
            LOG.log(Level.INFO, "StandardArtifactManager::archive()");
            this.standardArtifactManager.archive(workspace, launcher, buildListener, pathMap);
            return;
        }

        LOG.log(Level.INFO, "QiniuArtifactManager::archive()");
        final Map<String, String> artifacts = new HashMap<>();
        for (Map.Entry<String, String> entry : pathMap.entrySet()) {
            final String objectNameWithoutPrefix = entry.getKey();
            final String filePath = entry.getValue();
            artifacts.put(objectNameWithoutPrefix, filePath);
        }
        workspace.act(new QiniuUploader(this.accessKey, this.secretKey, this.bucketName, this.useHTTPs, this.infrequentStorage, artifacts, this.objectNamePrefixWithBuildNumber, buildListener));
    }

    @Override
    public boolean delete() throws IOException, InterruptedException {
        if (this.standardArtifactManager != null) {
            LOG.log(Level.INFO, "StandardArtifactManager::delete()");
            return this.standardArtifactManager.delete();
        }
        LOG.log(Level.INFO, "QiniuArtifactManager::delete()");

        final QiniuFile qiniuFile = (QiniuFile) this.root();
        return qiniuFile.deleteRecursively();
    }

    @Override
    public VirtualFile root() {
        if (this.standardArtifactManager != null) {
            LOG.log(Level.INFO, "StandardArtifactManager::root()");
            return this.standardArtifactManager.root();
        }
        LOG.log(Level.INFO, "QiniuArtifactManager::root()");
        final QiniuFileSystem qiniuFileSystem = QiniuFileSystem.create(
                this.accessKey, this.secretKey, this.bucketName,
                this.objectNamePrefixWithBuildNumber, this.downloadDomain, this.useHTTPs);
        return new QiniuFile(qiniuFileSystem, null);
    }
}
