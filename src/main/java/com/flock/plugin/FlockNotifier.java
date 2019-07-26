package com.flock.plugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.*;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import hudson.triggers.SCMTrigger;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.Cause;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

public class FlockNotifier extends hudson.tasks.Recorder {

    private String webhookUrl;

    private boolean notifyOnStart;
    private boolean notifyOnSuccess;
    private boolean notifyOnUnstable;
    private boolean notifyOnAborted;
    private boolean notifyOnFailure;
    private boolean notifyOnNotBuilt;

    @DataBoundConstructor
    public FlockNotifier(String webhookUrl, boolean notifyOnStart, boolean notifyOnSuccess, boolean notifyOnUnstable, boolean notifyOnAborted, boolean notifyOnFailure, boolean notifyOnNotBuilt) {
        this.webhookUrl = webhookUrl;
        this.notifyOnStart = notifyOnStart;
        this.notifyOnSuccess = notifyOnSuccess;
        this.notifyOnUnstable = notifyOnUnstable;
        this.notifyOnAborted = notifyOnAborted;
        this.notifyOnFailure = notifyOnFailure;
        this.notifyOnNotBuilt = notifyOnNotBuilt;
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

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (isNotifyOnStart()) {
            createPayload(build, true);
        }
        return super.prebuild(build, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if ((isNotifyOnSuccess() && build.getResult() == Result.SUCCESS)
                || (isNotifyOnAborted() && build.getResult() == Result.ABORTED)
                || (isNotifyOnFailure() && build.getResult() == Result.FAILURE)
                || (isNotifyOnNotBuilt() && build.getResult() == Result.NOT_BUILT)
                || (isNotifyOnUnstable() && build.getResult() == Result.UNSTABLE)) {
            sendNotification(build, false);
        }
        return true;
    }

    private void sendNotification(AbstractBuild build, boolean buildStarted) {
        System.out.print(createPayload(build, buildStarted));
        JSONObject payload = createPayload(build, buildStarted);
        try {
            makeRequest(payload);
        } catch (IOException e) {
            System.out.println("Ran into an IOException" + e.getLocalizedMessage());
        }
    }

    private JSONObject createPayload(AbstractBuild build, boolean buildStarted) {
        JSONObject jsonObject= new JSONObject();
        String runUrl = build.getUrl();
        String status;
        if (buildStarted) {
            status = "STARTED";
        } else {
            status = build.getResult().toString();
        }

        jsonObject.put("projectName", build.getProject().getDisplayName());
        jsonObject.put("displayName", build.getDisplayName());

        jsonObject.put("status", status);
        jsonObject.put("duration", build.getDurationString());
        jsonObject.put("runURL", runUrl);

        jsonObject.put("changes", getChanges(build));
        jsonObject.put("causeAction", getCauses(build));

        return jsonObject;
    }

    private void makeRequest(JSONObject payload) throws IOException {
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
        System.out.println("POST Response Code :: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) { //success
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // print result
            System.out.println(response.toString());
        } else {
            System.out.println("POST request not worked");
        }
    }

    private JSONObject getCauses(AbstractBuild b) {
        JSONObject jsonObject = new JSONObject();
        CauseAction causeAction = b.getAction(CauseAction.class);
        if (causeAction != null) {
            Cause scmCause = causeAction.findCause(SCMTrigger.SCMTriggerCause.class);
            StringBuilder causeActionStringBuilder = new StringBuilder();
            causeAction.getCauses().forEach( (cause) -> {
                causeActionStringBuilder.append(cause.getShortDescription());
            });
            String causeActionString = causeActionStringBuilder.toString();
            if (scmCause == null) {
                jsonObject.put("isSCM", false);
            } else {
                jsonObject.put("isSCM", true);
            }
            jsonObject.put("other", causeActionString);
        }
        return jsonObject;
    }

    JSONObject getChanges(AbstractBuild build) {
        ArrayList<String> authors = new ArrayList<String>();
        HashSet<String> affectedPaths = new HashSet<String>();
        for (Object item : build.getChangeSet().getItems()) {
            Entry entry = (Entry) item;
            authors.add(entry.getAuthor().getDisplayName());
            affectedPaths.addAll(entry.getAffectedPaths());
        }

        JSONObject json = new JSONObject();
        json.put("authors", authors);
        json.put("filesCount", affectedPaths.size());

        return json;
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
