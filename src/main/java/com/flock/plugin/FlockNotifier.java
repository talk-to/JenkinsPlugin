package com.flock.plugin;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import java.io.IOException;

public class FlockNotifier extends hudson.tasks.Recorder {

    private String webhookUrl;
    private boolean notifyOnStart;
    private boolean notifyOnSuccess;
    private boolean notifyOnUnstable;
    private boolean notifyOnAborted;
    private boolean notifyOnFailure;
    private boolean notifyOnNotBuilt;
    private boolean notifyOnRegression;
    private boolean notifyOnBackToNormal;

    @DataBoundConstructor
    public FlockNotifier(String webhookUrl, boolean notifyOnStart, boolean notifyOnSuccess, boolean notifyOnUnstable, boolean notifyOnAborted, boolean notifyOnFailure, boolean notifyOnNotBuilt, boolean notifyOnRegression, boolean notifyOnBackToNormal) {
        this.webhookUrl = webhookUrl;
        this.notifyOnStart = notifyOnStart;
        this.notifyOnSuccess = notifyOnSuccess;
        this.notifyOnUnstable = notifyOnUnstable;
        this.notifyOnAborted = notifyOnAborted;
        this.notifyOnFailure = notifyOnFailure;
        this.notifyOnNotBuilt = notifyOnNotBuilt;
        this.notifyOnRegression = notifyOnRegression;
        this.notifyOnBackToNormal = notifyOnBackToNormal;
    }

    // Getter methods below need to public for Config.jelly to fetch values if persisted.

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public boolean isNotifyOnStart() { return notifyOnStart; }

    public boolean isNotifyOnSuccess() { return notifyOnSuccess; }

    public boolean isNotifyOnUnstable() { return notifyOnUnstable; }

    public boolean isNotifyOnAborted() { return notifyOnAborted; }

    public boolean isNotifyOnFailure() { return notifyOnFailure; }

    public boolean isNotifyOnNotBuilt() { return notifyOnNotBuilt; }

    public boolean isNotifyOnRegression() { return notifyOnRegression; }

    public boolean isNotifyOnBackToNormal() { return notifyOnBackToNormal; }


    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (isNotifyOnStart()) {
            sendNotification(build, listener, true, null);
        }
        return super.prebuild(build, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        BuildResult buildResult = getBuildResult(build);
        if ((isNotifyOnSuccess() && buildResult == BuildResult.SUCCESS)
                || (isNotifyOnAborted() && buildResult == BuildResult.ABORTED)
                || (isNotifyOnFailure() && buildResult == BuildResult.FAILURE)
                || (isNotifyOnNotBuilt() && buildResult == BuildResult.NOT_BUILT)
                || (isNotifyOnUnstable() && buildResult == BuildResult.UNSTABLE)
                || (isNotifyOnBackToNormal() && buildResult == BuildResult.BACK_TO_NORMAL)
                || (isNotifyOnRegression() && buildResult == BuildResult.REGRESSION)) {
            sendNotification(build, listener, false, buildResult);
        }
        return true;
    }

    private BuildResult getBuildResult(AbstractBuild build) {
        Result result = build.getResult();
        Result previousResult;
        if(null != result) {
            AbstractBuild lastBuild = build.getProject().getLastBuild();
            if (lastBuild != null) {
                Run previousBuild = lastBuild.getPreviousBuild();
                Run previousSuccessfulBuild = build.getPreviousSuccessfulBuild();
                boolean buildHasSucceededBefore = previousSuccessfulBuild != null;

                /*
                 * If the last build was aborted, go back to find the last non-aborted build.
                 * This is so that aborted builds do not affect build transitions.
                 * I.e. if build 1 was failure, build 2 was aborted and build 3 was a success the transition
                 * should be failure -> success (and therefore back to normal) not aborted -> success.
                 */
                Run lastNonAbortedBuild = previousBuild;
                while (lastNonAbortedBuild != null && lastNonAbortedBuild.getResult() == Result.ABORTED) {
                    lastNonAbortedBuild = lastNonAbortedBuild.getPreviousBuild();
                }


                /* If all previous builds have been aborted, then use
                 * SUCCESS as a default status so an aborted message is sent
                 */
                if (lastNonAbortedBuild == null) {
                    previousResult = Result.SUCCESS;
                } else {
                    previousResult = lastNonAbortedBuild.getResult();
                }

                /* Back to normal should only be shown if the build has actually succeeded at some point.
                 * Also, if a build was previously unstable and has now succeeded the status should be
                 * "Back to normal"
                 */
                if (result == Result.SUCCESS
                        && (previousResult == Result.FAILURE || previousResult == Result.UNSTABLE)
                        && buildHasSucceededBefore && isNotifyOnBackToNormal()) {
                    return BuildResult.BACK_TO_NORMAL;
                }
                if (result == Result.SUCCESS) {
                    return BuildResult.SUCCESS;
                }
                if (result == Result.FAILURE) {
                    return BuildResult.FAILURE;
                }
                if (result == Result.ABORTED) {
                    return BuildResult.ABORTED;
                }
                if (result == Result.NOT_BUILT) {
                    return BuildResult.NOT_BUILT;
                }
                if (result == Result.UNSTABLE) {
                    return BuildResult.UNSTABLE;
                }
                if (lastNonAbortedBuild != null && previousResult != null && result.isWorseThan(previousResult)) {
                    return BuildResult.REGRESSION;
                }
            }
        }
        return null;
    }

    private void sendNotification(AbstractBuild build, BuildListener listener, boolean buildStarted, BuildResult buildResult) {
        JSONObject payload = PayloadManager.createPayload(build, buildStarted, buildResult);
        listener.getLogger().print(payload);
        try {
            RequestsManager.sendNotification(webhookUrl, payload, listener);
        } catch (IOException e) {
            listener.getLogger().print("Ran into an IOException" + e.getStackTrace());
        }
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Send Flock Notification";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            req.bindJSON(this, json);
            save();
            return super.configure(req, json);
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

}
