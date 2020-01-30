package io.jenkins.plugins;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.util.Secret;
import javax.annotation.Nonnull;
import java.io.Serializable;

public class QiniuConfig implements Serializable {
    @Nonnull
    private final String accessKey;
    @Nonnull
    private final Secret secretKey;
    @Nonnull
    private final String bucketName, objectNamePrefix, downloadDomain, upDomain;
    @Nonnull
    private final String rsDomain, rsfDomain, ucDomain, apiDomain;

    private final boolean useHTTPs, infrequentStorage, applyForAllJobs;

    public QiniuConfig(@Nonnull final String accessKey, @Nonnull final Secret secretKey, @Nonnull final String bucketName,
                       @Nonnull final String objectNamePrefix, @Nonnull final String downloadDomain, @Nonnull final String upDomain,
                       @Nonnull final String rsDomain, @Nonnull final String rsfDomain,
                       @Nonnull final String ucDomain, @Nonnull final String apiDomain,
                       final boolean useHTTPs, final boolean infrequentStorage, final boolean applyForAllJobs) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectNamePrefix = objectNamePrefix;
        this.downloadDomain = downloadDomain;
        this.upDomain = upDomain;
        this.rsDomain = rsDomain;
        this.rsfDomain = rsfDomain;
        this.ucDomain = ucDomain;
        this.apiDomain = apiDomain;
        this.useHTTPs = useHTTPs;
        this.infrequentStorage = infrequentStorage;
        this.applyForAllJobs = applyForAllJobs;
    }

    @Nonnull
    public BucketManager getBucketManager() {
        Initializer.setAppName();
        return new BucketManager(this.getAuth(), this.getConfiguration());
    }

    @Nonnull
    public Auth getAuth() {
        return Auth.create(this.accessKey, this.secretKey.getPlainText());
    }

    @Nonnull
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "I must set static variable here")
    public Configuration getConfiguration() {
        final String rsDomain = Util.fixEmptyAndTrim(this.rsDomain);
        final String ucDomain = Util.fixEmptyAndTrim(this.ucDomain);
        final String apiDomain = Util.fixEmptyAndTrim(this.apiDomain);
        final String upDomain = Util.fixEmptyAndTrim(this.upDomain);
        final String rsfDomain = Util.fixEmptyAndTrim(this.rsfDomain);

        if (rsDomain != null && !Configuration.defaultRsHost.equals(rsDomain)) {
            Configuration.defaultRsHost = rsDomain;
        }
        if (ucDomain != null && !Configuration.defaultUcHost.equals(ucDomain)) {
            Configuration.defaultUcHost = ucDomain;
        }
        if (apiDomain != null && !Configuration.defaultApiHost.equals(apiDomain)) {
            Configuration.defaultApiHost = apiDomain;
        }

        final Configuration config = new Configuration();
        config.useHttpsDomains = this.useHTTPs;
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

    @Nonnull
    public String getAccessKey() {
        return this.accessKey;
    }

    @Nonnull
    public Secret getSecretKey() {
        return this.secretKey;
    }

    @Nonnull
    public String getBucketName() {
        return this.bucketName;
    }

    @Nonnull
    public String getObjectNamePrefix() {
        return this.objectNamePrefix;
    }

    @Nonnull
    public String getDownloadDomain() {
        return this.downloadDomain;
    }

    @Nonnull
    public String getUpDomain() {
        return this.upDomain;
    }

    @Nonnull
    public String getRsDomain() {
        return this.rsDomain;
    }

    @Nonnull
    public String getRsfDomain() {
        return this.rsfDomain;
    }

    @Nonnull
    public String getUcDomain() {
        return this.ucDomain;
    }

    @Nonnull
    public String getApiDomain() {
        return this.apiDomain;
    }

    public boolean isUseHTTPs() {
        return this.useHTTPs;
    }

    public boolean isInfrequentStorage() {
        return this.infrequentStorage;
    }

    public boolean isApplyForAllJobs() {
        return this.applyForAllJobs;
    }
}

