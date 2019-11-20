package io.jenkins.plugins;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import com.qiniu.util.Auth;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;

public class QiniuPublisher extends Publisher implements SimpleBuildStep {
    @DataBoundConstructor
    public QiniuPublisher() {
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        final PrintStream logger = taskListener.getLogger();
        logger.println("Uploading to Qiniu");
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("Qiniu")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private String accessKey, secretKey;

        public DescriptorImpl() {
            super(QiniuPublisher.class);
            load();
        }

        public FormValidation doCheckAccessKey(@QueryParameter String accessKey) throws IOException, ServletException {
            if (accessKey.isEmpty()) {
                return FormValidation.error(Messages.QiniuPublisher_DescriptorImpl_errors_accessKeyIsEmpty());
            }
            this.accessKey = accessKey;
            final Throwable err = this.checkAccessKeySecretKey();
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages.QiniuPublisher_DescriptorImpl_errors_invalidAccessKeySecretKey());
            }
        }

        public FormValidation doCheckSecretKey(@QueryParameter String secretKey) throws IOException, ServletException {
            if (secretKey.isEmpty()) {
                return FormValidation.error(Messages.QiniuPublisher_DescriptorImpl_errors_secretKeyIsEmpty());
            }
            this.secretKey = secretKey;
            final Throwable err = this.checkAccessKeySecretKey();
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages.QiniuPublisher_DescriptorImpl_errors_invalidAccessKeySecretKey());
            }
        }

        private Throwable checkAccessKeySecretKey() {
            if (this.accessKey != null && !this.accessKey.isEmpty() && this.secretKey != null && !this.secretKey.isEmpty()) {
                final BucketManager bucketManager = new BucketManager(Auth.create(this.accessKey, this.secretKey), new Configuration());
                try {
                    bucketManager.buckets();
                } catch (QiniuException e) {
                    return e;
                }
            }
            return null;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.QiniuPublisher_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            this.accessKey = json.getString("accessKey");
            this.secretKey = json.getString("secretKey");
            final Throwable err = this.checkAccessKeySecretKey();
            if (err != null) {
                throw new FormException(Messages.QiniuPublisher_DescriptorImpl_errors_invalidAccessKeySecretKey(), err, "accessKey");
            }
            save();
            return super.configure(req, json);
        }

        public String getAccessKey() {
            return this.accessKey;
        }

        public String getSecretKey() {
            return this.secretKey;
        }
    }
}
