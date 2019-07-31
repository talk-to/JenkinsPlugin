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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
            sendNotification(build, listener, true);
        }
        return super.prebuild(build, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if ((isNotifyOnSuccess() && build.getResult() == Result.SUCCESS)
                || (isNotifyOnAborted() && build.getResult() == Result.ABORTED)
                || (isNotifyOnFailure() && build.getResult() == Result.FAILURE)
                || (isNotifyOnNotBuilt() && build.getResult() == Result.NOT_BUILT)
                || (isNotifyOnUnstable() && build.getResult() == Result.UNSTABLE)
                || (isNotifyOnBackToNormal() && PayloadManager.getStatusMessage(build, isNotifyOnBackToNormal()).contains("back to normal"))
                || (isNotifyOnRegression() && PayloadManager.getStatusMessage(build, isNotifyOnBackToNormal()).contains("regression"))) {
            sendNotification(build, listener, false);
        }
        return true;
    }

    private void sendNotification(AbstractBuild build, BuildListener listener, boolean buildStarted) {
        JSONObject payload = PayloadManager.createPayload(build, buildStarted, isNotifyOnBackToNormal());
        listener.getLogger().print(payload);
        try {
            makeRequest(payload, listener);
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

    private void makeRequest(JSONObject payload, BuildListener listener) throws IOException {
        URL url = new URL(webhookUrl);

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");

        // For POST only - START
        con.setDoOutput(true);

        OutputStream os = con.getOutputStream();
        os.write(payload.toString().getBytes());
        os.flush();
        os.close();
        // For POST only - END

        int responseCode = con.getResponseCode();
        listener.getLogger().print("POST Response Code :: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) { //success
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            listener.getLogger().print(response.toString());
        } else {
            listener.getLogger().print("POST request not worked");
        }
    }

}
