package io.jenkins.plugins;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

@Restricted(NoExternalUse.class)
public class QiniuArtifactManagerFactory extends ArtifactManagerFactory {
    public static final boolean applyForAllJobs = false;
    private static final Logger LOG = Logger.getLogger(QiniuArtifactManagerFactory.class.getName());
    private static final String DEFAULT_RS_HOST = Configuration.defaultRsHost;
    private static final String DEFAULT_API_HOST = Configuration.defaultApiHost;
    private static final String DEFAULT_UC_HOST = Configuration.defaultUcHost;
    private final QiniuConfig config;

    @DataBoundConstructor
    public QiniuArtifactManagerFactory(@Nonnull String accessKey, @Nonnull final Secret secretKey, @Nonnull String bucketName,
                                       @Nonnull String objectNamePrefix, @Nonnull String downloadDomain,
                                       @Nonnull String rsDomain, @Nonnull String rsfDomain, @Nonnull String ucDomain,
                                       @Nonnull String apiDomain, @Nonnull String upDomain,
                                       final boolean useHTTPs, final boolean infrequentStorage) {
        accessKey = Util.fixEmptyAndTrim(accessKey);
        bucketName = Util.fixEmptyAndTrim(bucketName);
        downloadDomain = Util.fixEmptyAndTrim(downloadDomain);
        upDomain = Util.fixEmptyAndTrim(upDomain);
        rsDomain = Util.fixEmptyAndTrim(rsDomain);
        rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
        ucDomain = Util.fixEmptyAndTrim(ucDomain);
        apiDomain = Util.fixEmptyAndTrim(apiDomain);
        if (accessKey == null) {
            throw new IllegalArgumentException("accessKey must not be null or empty");
        } else if (secretKey == null || Util.fixEmptyAndTrim(secretKey.getPlainText()) == null) {
            throw new IllegalArgumentException("secretKey must not be null or empty");
        } else if (bucketName == null) {
            throw new IllegalArgumentException("bucketName must not be null or empty");
        }
        if (upDomain == null) {
            upDomain = "";
        }
        if (rsDomain == null) {
            rsDomain = "";
        }
        if (rsfDomain == null) {
            rsfDomain = "";
        }
        if (ucDomain == null) {
            ucDomain = "";
        }
        if (apiDomain == null) {
            apiDomain = "";
        }
        if (downloadDomain == null) {
            downloadDomain = "";
        }
        final QiniuConfig config = new QiniuConfig(accessKey, secretKey, bucketName, objectNamePrefix, downloadDomain,
                upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs, infrequentStorage, applyForAllJobs);
        if (downloadDomain.isEmpty()) {
            try {
                final String[] domainList = config.getBucketManager().domainList(config.getBucketName());
                if (domainList.length > 0) {
                    this.config = new QiniuConfig(accessKey, secretKey, bucketName, objectNamePrefix, domainList[domainList.length - 1],
                            upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs, infrequentStorage, applyForAllJobs);
                } else {
                    throw new CannotGetDownloadDomain("Bucket " + config.getBucketName() + " are not bound with any download domain");
                }
            } catch (QiniuException e) {
                throw new QiniuRuntimeException(e);
            }
        } else {
            this.config = config;
        }
        LOG.log(Level.INFO, "QiniuArtifactManagerFactory is configured: accessKey={0}, bucketName={1}, downloadDomain={2}",
                new Object[]{accessKey, bucketName, downloadDomain});
    }

    @Nonnull
    @Override
    public ArtifactManager managerFor(Run<?, ?> run) {
        LOG.log(Level.INFO, "QiniuArtifactManagerFactory creates QiniuArtifactManager");
        return new QiniuArtifactManager(run, this.config);
    }

    @CheckForNull
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptor(this.getClass());
    }

    @Extension
    public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.QiniuArtifactManagerFactory_DescriptorImpl_DisplayName();
        }

        @POST
        public FormValidation doCheckAccessKey(@QueryParameter String accessKey,
                                               @QueryParameter String bucketName,
                                               @QueryParameter String upDomain,
                                               @QueryParameter String rsDomain,
                                               @QueryParameter String rsfDomain,
                                               @QueryParameter String ucDomain,
                                               @QueryParameter String apiDomain,
                                               @QueryParameter final Secret secretKey,
                                               @QueryParameter final boolean useHTTPs) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (accessKey == null) {
                return FormValidation.error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_accessKeyIsEmpty());
            }
            final Throwable err = this.checkAccessKeySecretKeyAndBucketName(accessKey, secretKey, bucketName, upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAccessKeySecretKeyAndBucketName());
            }
        }

        @POST
        public FormValidation doCheckSecretKey(@QueryParameter final Secret secretKey,
                                               @QueryParameter String accessKey,
                                               @QueryParameter String bucketName,
                                               @QueryParameter String upDomain,
                                               @QueryParameter String rsDomain,
                                               @QueryParameter String rsfDomain,
                                               @QueryParameter String ucDomain,
                                               @QueryParameter String apiDomain,
                                               @QueryParameter final boolean useHTTPs) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (secretKey == null || Util.fixEmptyAndTrim(secretKey.getPlainText()) == null) {
                return FormValidation.error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_secretKeyIsEmpty());
            }
            final Throwable err = this.checkAccessKeySecretKeyAndBucketName(accessKey, secretKey, bucketName, upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAccessKeySecretKeyAndBucketName());
            }
        }

        @POST
        public FormValidation doCheckBucketName(@QueryParameter String bucketName,
                                                @QueryParameter String accessKey,
                                                @QueryParameter String upDomain,
                                                @QueryParameter String rsDomain,
                                                @QueryParameter String rsfDomain,
                                                @QueryParameter String ucDomain,
                                                @QueryParameter String apiDomain,
                                                @QueryParameter final Secret secretKey,
                                                @QueryParameter final boolean useHTTPs) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (bucketName == null) {
                return FormValidation.error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_bucketNameIsEmpty());
            }
            final Throwable err = this.checkAccessKeySecretKeyAndBucketName(accessKey, secretKey, bucketName, upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAccessKeySecretKeyAndBucketName());
            }
        }

        @POST
        public FormValidation doCheckDownloadDomain(@QueryParameter String downloadDomain,
                                                    @QueryParameter String accessKey,
                                                    @QueryParameter String bucketName,
                                                    @QueryParameter String upDomain,
                                                    @QueryParameter String rsDomain,
                                                    @QueryParameter String rsfDomain,
                                                    @QueryParameter String ucDomain,
                                                    @QueryParameter String apiDomain,
                                                    @QueryParameter final Secret secretKey,
                                                    @QueryParameter final boolean useHTTPs) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            downloadDomain = Util.fixEmptyAndTrim(downloadDomain);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (downloadDomain != null) {
                try {
                    new URL("http://" + downloadDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidDownloadDomain());
                }
            }

            if (!this.checkDownloadDomain(accessKey, secretKey, bucketName, downloadDomain, upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs)) {
                return FormValidation.error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_downloadDomainIsEmpty());
            }

            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckUpDomain(@QueryParameter String upDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            if (upDomain != null) {
                try {
                    new URL("http://" + upDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidUpDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckRsDomain(@QueryParameter String rsDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            if (rsDomain != null) {
                try {
                    new URL("http://" + rsDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidRsDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckRsfDomain(@QueryParameter String rsfDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            if (rsfDomain != null) {
                try {
                    new URL("http://" + rsfDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidRsfDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckUcDomain(@QueryParameter String ucDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            if (ucDomain != null) {
                try {
                    new URL("http://" + ucDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidUcDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckApiDomain(@QueryParameter String apiDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (apiDomain != null) {
                try {
                    new URL("http://" + apiDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err, Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAPIDomain());
                }
            }
            return FormValidation.ok();
        }

        private Throwable checkAccessKeySecretKeyAndBucketName(final String accessKey, final Secret secretKey,
                                                               final String bucketName, final String upDomain,
                                                               final String rsDomain, final String rsfDomain,
                                                               final String ucDomain, final String apiDomain,
                                                               final boolean useHTTPs) {
            final BucketManager bucketManager = this.getBucketManager(accessKey, secretKey, upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (bucketManager != null && bucketName != null) {
                try {
                    bucketManager.getBucketInfo(bucketName);
                } catch (QiniuException e) {
                    return e;
                }
            }
            return null;
        }

        private boolean checkDownloadDomain(final String accessKey, final Secret secretKey,
                                            final String bucketName, final String downloadDomain,
                                            final String upDomain, final String rsDomain,
                                            final String rsfDomain, final String ucDomain,
                                            final String apiDomain, final boolean useHTTPs) {
            final BucketManager bucketManager = this.getBucketManager(accessKey, secretKey, upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (bucketManager != null &&
                    bucketName != null &&
                    downloadDomain == null) {
                try {
                    final String[] domainList = bucketManager.domainList(bucketName);
                    return domainList.length > 0;
                } catch (QiniuException e) {
                    return false;
                }
            }
            return true;
        }

        @CheckForNull
        private BucketManager getBucketManager(final String accessKey, final Secret secretKey,
                                               final String upDomain, final String rsDomain,
                                               final String rsfDomain, final String ucDomain,
                                               final String apiDomain, final boolean useHTTPs) {
            Initializer.setAppName();

            final Auth auth = this.getAuth(accessKey, secretKey);
            if (auth == null) {
                return null;
            }
            final Configuration config = this.getConfiguration(upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            return new BucketManager(auth, config);
        }

        @CheckForNull
        private Auth getAuth(final String accessKey, final Secret secretKey) {
            String secretKeyPlainText = null;

            if (secretKey != null) {
                secretKeyPlainText = Util.fixEmptyAndTrim(secretKey.getPlainText());
            }
            if (accessKey == null || secretKeyPlainText == null) {
                return null;
            }
            return Auth.create(accessKey, secretKeyPlainText);
        }

        @Nonnull
        private Configuration getConfiguration(String upDomain, String rsDomain, String rsfDomain,
                                               String ucDomain, String apiDomain, final boolean useHTTPs) {
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);

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
            config.useHttpsDomains = useHTTPs;
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

    public static class QiniuRuntimeException extends RuntimeException {
        public QiniuRuntimeException(final Throwable cause) {
            super(cause);
        }
    }

    public static class CannotGetDownloadDomain extends IllegalArgumentException {
        public CannotGetDownloadDomain(final String s) {
            super(s);
        }
    }

    @Nonnull
    public BucketManager getBucketManager() {
        return this.config.getBucketManager();
    }

    @Nonnull
    public Auth getAuth() {
        return this.config.getAuth();
    }

    @Nonnull
    public Configuration getConfiguration() {
        return this.config.getConfiguration();
    }

    @Nonnull
    public String getAccessKey() {
        return this.config.getAccessKey();
    }

    @Nonnull
    public Secret getSecretKey() {
        return this.config.getSecretKey();
    }

    @Nonnull
    public String getBucketName() {
        return this.config.getBucketName();
    }

    @Nonnull
    public String getObjectNamePrefix() {
        return this.config.getObjectNamePrefix();
    }

    @Nonnull
    public String getDownloadDomain() {
        return this.config.getDownloadDomain();
    }

    @Nonnull
    public String getUpDomain() {
        return this.config.getUpDomain();
    }

    @Nonnull
    public String getRsDomain() {
        return this.config.getRsDomain();
    }

    @Nonnull
    public String getRsfDomain() {
        return this.config.getRsfDomain();
    }

    @Nonnull
    public String getUcDomain() {
        return this.config.getUcDomain();
    }

    @Nonnull
    public String getApiDomain() {
        return this.config.getApiDomain();
    }

    public boolean isUseHTTPs() {
        return this.config.isUseHTTPs();
    }

    public boolean isInfrequentStorage() {
        return this.config.isInfrequentStorage();
    }

    public boolean isApplyForAllJobs() {
        return this.config.isApplyForAllJobs();
    }
}
