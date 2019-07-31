package com.flock.plugin;

import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;
import net.sf.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;

public class PayloadManager {

    public static JSONObject createPayload(AbstractBuild build, boolean buildStarted, BuildResult buildResult) {
        JSONObject jsonObject= new JSONObject();
        if (buildStarted) {
            jsonObject.put("status", "start");
        } else {
            jsonObject.put("status", buildResult.stringValue());
            jsonObject.put("duration", getDuration(build));
        }

        jsonObject.put("projectName", build.getProject().getDisplayName());
        jsonObject.put("displayName", build.getDisplayName());
        jsonObject.put("runURL", build.getAbsoluteUrl());

        jsonObject.put("changes", getChanges(build));
        jsonObject.put("causeAction", getCauses(build));

        return jsonObject;
    }

    private static long getDuration(AbstractBuild build) {
        long buildStartTime = build.getStartTimeInMillis();
        long currentTimeMillis = System.currentTimeMillis();

        return (currentTimeMillis - buildStartTime)/1000;
    }

    private static JSONObject getCauses(AbstractBuild b) {
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

    private static JSONObject getChanges(AbstractBuild build) {
        ArrayList<String> authors = new ArrayList<String>();
        HashSet<String> affectedPaths = new HashSet<String>();
        for (Object item : build.getChangeSet().getItems()) {
            ChangeLogSet.Entry entry = (ChangeLogSet.Entry) item;
            authors.add(entry.getAuthor().getDisplayName());
            affectedPaths.addAll(entry.getAffectedPaths());
        }

        JSONObject json = new JSONObject();
        json.put("authors", authors);
        json.put("filesCount", affectedPaths.size());
        return json;
    }
}
