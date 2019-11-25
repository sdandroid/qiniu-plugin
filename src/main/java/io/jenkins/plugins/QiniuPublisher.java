package io.jenkins.plugins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
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
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.logging.Logger;

public class QiniuPublisher extends Recorder implements SimpleBuildStep {
    private static final Logger LOG = Logger.getLogger(QiniuPublisher.class.getName());
    private String includeFilesGlob, excludeFilesGlob;
    private boolean doNotFailIfArchiveNothing = false, archiveIfBuildIsSuccessful = true, useDefaultExcludes = true, caseSensitive = true;

    // TODO: Support infrequent storage

    @DataBoundConstructor
    public QiniuPublisher(@Nonnull String includeFilesGlob, @Nonnull String excludeFilesGlob, boolean doNotFailIfArchiveNothing, boolean archiveIfBuildIsSuccessful, boolean useDefaultExcludes, boolean caseSensitive) {
        this.includeFilesGlob = includeFilesGlob;
        this.excludeFilesGlob = excludeFilesGlob;
        this.doNotFailIfArchiveNothing = doNotFailIfArchiveNothing;
        this.archiveIfBuildIsSuccessful = archiveIfBuildIsSuccessful;
        this.useDefaultExcludes = useDefaultExcludes;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        final PrintStream logger = taskListener.getLogger();
        final EnvVars envVars = run.getEnvironment(taskListener);
        logger.println("Uploading to Qiniu");

        if (QiniuStore.getQiniuArtifactManagerFactory() == null) {
            throw new RuntimeException("You may not configured Qiniu Jenkins plugin");
        }
        final QiniuArtifactManager artifactManager = (QiniuArtifactManager) run.pickArtifactManager();

        final ListFiles listFiles = new ListFiles(envVars.expand(this.includeFilesGlob), envVars.expand(this.excludeFilesGlob), this.useDefaultExcludes, this.caseSensitive);
        final Map<String, String> files = filePath.act(listFiles);

        artifactManager.archive(filePath, launcher, BuildListenerAdapter.wrap(taskListener), files);
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

    public boolean isDoNotFailIfArchiveNothing() {
        return this.doNotFailIfArchiveNothing;
    }

    public void setDoNotFailIfArchiveNothing(boolean doNotFailIfArchiveNothing) {
        this.doNotFailIfArchiveNothing = doNotFailIfArchiveNothing;
    }

    public boolean isArchiveIfBuildIsSuccessful() {
        return this.archiveIfBuildIsSuccessful;
    }

    public void setArchiveIfBuildIsSuccessful(boolean archiveIfBuildIsSuccessful) {
        this.archiveIfBuildIsSuccessful = archiveIfBuildIsSuccessful;
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
