package io.jenkins.plugins;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import sun.security.krb5.Config;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
class QiniuUploader extends MasterToSlaveFileCallable<Void> {
    private static final Logger LOG = Logger.getLogger(QiniuUploader.class.getName());
    private static final String DEFAULT_RS_HOST = Configuration.defaultRsHost;
    private static final String DEFAULT_API_HOST = Configuration.defaultApiHost;
    private static final String DEFAULT_UC_HOST = Configuration.defaultUcHost;

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
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "I must set static variable here")
    private Configuration getConfiguration() {
        final String rsDomain = Util.fixEmptyAndTrim(this.config.getRsDomain());
        final String ucDomain = Util.fixEmptyAndTrim(this.config.getUcDomain());
        final String apiDomain = Util.fixEmptyAndTrim(this.config.getApiDomain());
        final String upDomain = Util.fixEmptyAndTrim(this.config.getUpDomain());
        final String rsfDomain = Util.fixEmptyAndTrim(this.config.getRsfDomain());

        if (rsDomain != null && !Configuration.defaultRsHost.equals(rsDomain)) {
            Configuration.defaultRsHost = rsDomain;
        } else if (rsDomain == null) {
            Configuration.defaultRsHost = DEFAULT_RS_HOST;
        }

        if (ucDomain != null && !Configuration.defaultUcHost.equals(ucDomain)) {
            Configuration.defaultUcHost = ucDomain;
        } else if (ucDomain == null) {
            Configuration.defaultUcHost = DEFAULT_UC_HOST;
        }

        if (apiDomain != null && !Configuration.defaultApiHost.equals(apiDomain)) {
            Configuration.defaultApiHost = apiDomain;
        } else if (apiDomain == null) {
            Configuration.defaultApiHost = DEFAULT_API_HOST;
        }

        final Configuration config = new Configuration();
        config.useHttpsDomains = this.config.isUseHTTPs();
        config.region = mayCreateRegion(upDomain, rsDomain, rsfDomain, apiDomain);
        return config;
    }

    private Region mayCreateRegion(final String upDomain, final String rsDomain, final String rsfDomain, final String apiDomain) {
        boolean returnsNull = true;
        Region.Builder regionBuilder = new Region.Builder();
        if (upDomain != null) {
            regionBuilder = regionBuilder.accUpHost(upDomain).srcUpHost(upDomain);
            returnsNull = false;
        }
        if (rsDomain != null) {
            regionBuilder = regionBuilder.rsHost(rsDomain);
            returnsNull = false;
        }
        if (rsfDomain != null) {
            regionBuilder = regionBuilder.rsfHost(rsfDomain);
            returnsNull = false;
        }
        if (apiDomain != null) {
            regionBuilder = regionBuilder.apiHost(apiDomain);
            returnsNull = false;
        }
        if (returnsNull) {
            return null;
        }
        return regionBuilder.build();
    }
}
