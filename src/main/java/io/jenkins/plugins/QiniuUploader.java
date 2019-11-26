package io.jenkins.plugins;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class QiniuUploader extends MasterToSlaveFileCallable<Void> {
    private static final Logger LOG = Logger.getLogger(QiniuUploader.class.getName());

    private final String accessKey, secretKey, bucketName, objectNamePrefix;
    private final boolean useHTTPs, infrequentStorage;
    private final Map<String, String> artifactURLs;
    private final TaskListener listener;

    QiniuUploader(@Nonnull String accessKey, @Nonnull String secretKey, @Nonnull String bucketName,
                  boolean useHTTPs, boolean infrequentStorage, @Nonnull Map<String, String> artifactURLs,
                  @Nonnull String objectNamePrefix, @Nonnull TaskListener listener) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.useHTTPs = useHTTPs;
        this.infrequentStorage = infrequentStorage;
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
        final Auth auth = Auth.create(this.accessKey, this.secretKey);
        final String uploadToken = auth.uploadToken(this.bucketName, null, 24 * 3600, new StringMap().put("fileType", 1));

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
        final Configuration config = new Configuration();
        config.useHttpsDomains = this.useHTTPs;
        return config;
    }
}
