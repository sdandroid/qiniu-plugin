package io.jenkins.plugins;

import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.Publisher;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QiniuArtifactManagerFactory extends ArtifactManagerFactory {
    private static final Logger LOG = Logger.getLogger(QiniuArtifactManagerFactory.class.getName());
    private String accessKey, secretKey, bucketName, objectNamePrefix, downloadDomain;
    private boolean useHTTPs, infrequentStorage;

    @DataBoundConstructor
    public QiniuArtifactManagerFactory(@Nonnull String accessKey, @Nonnull String secretKey, @Nonnull String bucketName,
                                       @Nonnull String objectNamePrefix,
                                       @Nonnull String downloadDomain, boolean useHTTPs, boolean infrequentStorage) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectNamePrefix = objectNamePrefix;
        this.downloadDomain = downloadDomain;
        this.useHTTPs = useHTTPs;
        this.infrequentStorage = infrequentStorage;
    }

    @Override
    public ArtifactManager managerFor(Run<?, ?> run) {
        if (run.getParent() instanceof AbstractProject) {
            if (QiniuArtifactManagerFactory.isQiniuPublisherConfigured((AbstractProject) run.getParent())) {
                LOG.log(Level.INFO, "QiniuArtifactManagerFactory creates QiniuArtifactManager");
                return new QiniuArtifactManager(run,
                        this.accessKey, this.secretKey, this.bucketName, this.objectNamePrefix,
                        this.downloadDomain, this.useHTTPs, this.infrequentStorage);
            }
        }
        LOG.log(Level.INFO, "QiniuArtifactManagerFactory creates nothing, which means it won't decide which ArtifactManager will be created");
        return null;
    }

    static boolean isQiniuPublisherConfigured(AbstractProject<?, ?> project) {
        final List<Publisher> publishers = project.getPublishersList();
        for (Publisher publisher : publishers) {
            if (publisher.getClass().equals(QiniuPublisher.class)) {
                return true;
            }
        }
        return false;
    }

    public String getAccessKey() {
        return this.accessKey;
    }

    public String getSecretKey() {
        return this.secretKey;
    }

    public String getBucketName() {
        return this.bucketName;
    }

    public String getObjectNamePrefix() {
        return this.objectNamePrefix;
    }

    public String getDownloadDomain() {
        return this.downloadDomain;
    }

    public boolean isUseHTTPs() {
        return this.useHTTPs;
    }

    public boolean isInfrequentStorage() {
        return this.infrequentStorage;
    }

    public void setAccessKey(final String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretKey(final String secretKey) {
        this.secretKey = secretKey;
    }

    public void setBucketName(final String bucketName) {
        this.bucketName = bucketName;
    }

    public void setObjectNamePrefix(final String objectNamePrefix) {
        this.objectNamePrefix = objectNamePrefix;
    }

    public void setDownloadDomain(final String downloadDomain) {
        this.downloadDomain = downloadDomain;
    }

    public void setUseHTTPs(final boolean useHTTPs) {
        this.useHTTPs = useHTTPs;
    }

    public void setInfrequentStorage(final boolean infrequentStorage) {
        this.infrequentStorage = infrequentStorage;
    }
}
