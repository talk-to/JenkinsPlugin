package com.flock.plugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import java.io.IOException;
import hudson.scm.ChangeLogSet.*;
import hudson.scm.ChangeLogSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;



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

    public Boolean isNotifyOnStart() { return notifyOnStart; }

    public boolean isNotifyOnSuccess() { return notifyOnSuccess; }

    public boolean isNotifyOnUnstable() { return notifyOnUnstable; }

    public boolean isNotifyOnAborted() { return notifyOnAborted; }

    public boolean isNotifyOnFailure() { return notifyOnFailure; }

    public boolean isNotifyOnNotBuilt() { return notifyOnNotBuilt; }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        listener.getLogger().println("Build started");
        return super.prebuild(build, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        sendNotification(build);
        return true;
    }

    private void sendNotification(AbstractBuild build) {
        System.out.print(createPayload(build));
    }

    private JSONObject createPayload(AbstractBuild build) {
        JSONObject jsonObject= new JSONObject();
        String runUrl = build.getUrl();

        jsonObject.put("projectName", build.getProject().getDisplayName());
        jsonObject.put("displayName", build.getDisplayName());

        jsonObject.put("status", build.getResult().toString());
        jsonObject.put("duration", build.getDurationString());
        jsonObject.put("runURL", runUrl);
//        jsonObject.put("causeAction", causesString);

        JSONObject changes = getChanges(build);
        jsonObject.put("changes", changes);
        return jsonObject;
    }

    private JSONObject getCauses(AbstractBuild b) {
        JSONObject jsonObject = new JSONObject();
//        jsonObject.put("isSCM", b.);
        List<Cause> causes = b.getCauses();
        StringBuilder causesString = new StringBuilder();
        causes.forEach((cause) -> {
            causesString.append(cause.getShortDescription()).append("\n");
        });
        return jsonObject;
    }

    JSONObject getChanges(AbstractBuild r) {
        if (!r.hasChangeSetComputed()) {
            return null; // FIXME: Check when no-changeset will be there. Author and files changes should be present
        }
        ChangeLogSet changeSet = r.getChangeSet();
        List<Entry> entries = new LinkedList<>();
        Set<AffectedFile> files = new HashSet<>();
        for (Object o : changeSet.getItems()) {
            Entry entry = (Entry) o;
            entries.add(entry);
            if (CollectionUtils.isNotEmpty(entry.getAffectedFiles())) {
                files.addAll(entry.getAffectedFiles());
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        Set<String> authors = new HashSet<>();
        for (Entry entry : entries) {
            authors.add(entry.getAuthor().getDisplayName());
        }

        JSONObject json = new JSONObject();
        json.put("authors", authors);
        json.put("filesCount", files.size());

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
