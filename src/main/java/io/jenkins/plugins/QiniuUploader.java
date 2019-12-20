package io.jenkins.plugins;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
class QiniuUploader extends MasterToSlaveFileCallable<Void> {
    private static final Logger LOG = Logger.getLogger(QiniuUploader.class.getName());

    private final String objectNamePrefix;
    private final QiniuArtifactManager.Marker marker;
    private final QiniuConfig config;
    private final Map<String, String> artifactURLs;
    private final TaskListener listener;

    QiniuUploader(@Nonnull QiniuConfig config, @Nonnull QiniuArtifactManager.Marker marker,
                  @Nonnull Map<String, String> artifactURLs, @Nonnull String objectNamePrefix,
                  @Nonnull TaskListener listener) {
        this.config = config;
        this.marker = marker;
        this.artifactURLs = artifactURLs;
        this.objectNamePrefix = objectNamePrefix;
        this.listener = listener;
    }

    @Override
    public Void invoke(File root, VirtualChannel virtualChannel) throws IOException, InterruptedException {
        if (this.artifactURLs.isEmpty()) {
            return null;
        }

        final UploadManager uploadManager = new UploadManager(this.getConfiguration());
        final Auth auth = Auth.create(this.config.getAccessKey(), this.config.getSecretKey().getPlainText());
        StringMap params = new StringMap().put("insertOnly", 1);
        if (this.config.isInfrequentStorage()) {
            params = params.put("fileType", 1);
        }
        final String uploadToken = auth.uploadToken(this.config.getBucketName(), null, 24 * 3600, params);

        Initializer.setAppName();

        try {
            for (Map.Entry<String, String> entry : this.artifactURLs.entrySet()) {
                final String objectName = this.objectNamePrefix + entry.getKey();
                final File file = new File(root, entry.getValue());
                uploadManager.put(file, objectName, uploadToken, null, null, true);
                LOG.log(Level.INFO, "Qiniu upload {0} to {1}", new Object[]{file.getAbsolutePath(), objectName});
            }
        } finally {
            this.listener.getLogger().flush();
        }
        LOG.log(Level.INFO, "Qiniu uploading is done");
        return null;
    }

    @Nonnull
    private Configuration getConfiguration() {
        if (!Configuration.defaultRsHost.equals(this.config.getRsDomain())) {
            Configuration.defaultRsHost = this.config.getRsDomain();
        }
        if (!Configuration.defaultUcHost.equals(this.config.getUcDomain())) {
            Configuration.defaultUcHost = this.config.getUcDomain();
        }
        if (!Configuration.defaultApiHost.equals(this.config.getApiDomain())) {
            Configuration.defaultApiHost = this.config.getApiDomain();
        }

        final Configuration config = new Configuration();
        config.useHttpsDomains = this.config.isUseHTTPs();
        return config;
    }
}
