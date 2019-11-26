package io.jenkins.plugins;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.Logger;

public class QiniuPublisher extends Recorder implements SimpleBuildStep {
    private static final Logger LOG = Logger.getLogger(QiniuPublisher.class.getName());
    private String includeFilesGlob, excludeFilesGlob;
    private boolean allowEmptyArchive = false, onlyIfSuccessful = true, useDefaultExcludes = true, caseSensitive = true;

    @DataBoundConstructor
    public QiniuPublisher(
            @Nonnull String includeFilesGlob, @Nonnull String excludeFilesGlob,
            boolean allowEmptyArchive, boolean onlyIfSuccessful,
            boolean useDefaultExcludes, boolean caseSensitive) {
        this.includeFilesGlob = includeFilesGlob;
        this.excludeFilesGlob = excludeFilesGlob;
        this.allowEmptyArchive = allowEmptyArchive;
        this.onlyIfSuccessful = onlyIfSuccessful;
        this.useDefaultExcludes = useDefaultExcludes;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public void perform(
            @Nonnull Run<?, ?> run, @Nonnull FilePath filePath,
            @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        final PrintStream logger = taskListener.getLogger();
        final EnvVars envVars = run.getEnvironment(taskListener);

        if (QiniuStore.getQiniuArtifactManagerFactory() == null) {
            throw new AbortException(Messages.QiniuPublisher_NotConfigured());
        }
        final QiniuArtifactManager artifactManager = (QiniuArtifactManager) run.pickArtifactManager();

        final Result result = run.getResult();

        if (this.onlyIfSuccessful && result != null && result.isWorseThan(Result.UNSTABLE)) {
            logger.println(hudson.tasks.Messages.ArtifactArchiver_SkipBecauseOnlyIfSuccessful());
            return;
        }
        logger.println(Messages.QiniuPublisher_ARCHIVING_ARTIFACTS());

        final ListFiles listFiles = new ListFiles(
                envVars.expand(this.includeFilesGlob),
                envVars.expand(this.excludeFilesGlob),
                this.useDefaultExcludes, this.caseSensitive);
        final Map<String, String> files = filePath.act(listFiles);

        if (!files.isEmpty()) {
            artifactManager.archive(filePath, launcher, BuildListenerAdapter.wrap(taskListener), files);
        } else {
            if (result == null || result.isBetterOrEqualTo(Result.UNSTABLE)) {
                try {
                    String msg = filePath.validateAntFileMask(this.includeFilesGlob, FilePath.VALIDATE_ANT_FILE_MASK_BOUND, this.caseSensitive);
                    if (msg != null) {
                        logger.println(msg);
                    }
                } catch (Exception e) {
                    Functions.printStackTrace(e, logger);
                }
                if (this.allowEmptyArchive) {
                    logger.println(hudson.tasks.Messages.ArtifactArchiver_NoMatchFound(this.includeFilesGlob));
                } else {
                    throw new AbortException(hudson.tasks.Messages.ArtifactArchiver_NoMatchFound(this.includeFilesGlob));
                }
            }
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.STEP;
    }

    public String getIncludeFilesGlob() {
        return this.includeFilesGlob;
    }

    public void setIncludeFilesGlob(String includeFilesGlob) {
        this.includeFilesGlob = includeFilesGlob;
    }

    public String getExcludeFilesGlob() {
        return this.excludeFilesGlob;
    }

    public void setExcludeFilesGlob(String excludeFilesGlob) {
        this.excludeFilesGlob = excludeFilesGlob;
    }

    public boolean isAllowEmptyArchive() {
        return this.allowEmptyArchive;
    }

    public void setAllowEmptyArchive(boolean allowEmptyArchive) {
        this.allowEmptyArchive = allowEmptyArchive;
    }

    public boolean isOnlyIfSuccessful() {
        return this.onlyIfSuccessful;
    }

    public void setOnlyIfSuccessful(boolean onlyIfSuccessful) {
        this.onlyIfSuccessful = onlyIfSuccessful;
    }

    public boolean isUseDefaultExcludes() {
        return this.useDefaultExcludes;
    }

    public void setUseDefaultExcludes(boolean useDefaultExcludes) {
        this.useDefaultExcludes = useDefaultExcludes;
    }

    public boolean isCaseSensitive() {
        return this.caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Symbol("Qiniu")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super(QiniuPublisher.class);
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
    }
}
