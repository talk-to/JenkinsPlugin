package com.flock.plugin;

import hudson.model.*;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;
import net.sf.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;

public class PayloadManager {

    public static JSONObject createPayload(AbstractBuild build, boolean buildStarted, boolean shouldNotifyBackToNormal) {
        JSONObject jsonObject= new JSONObject();
        if (buildStarted) {
            jsonObject.put("status", "start");
        } else {
            jsonObject.put("status", getStatusMessage(build, shouldNotifyBackToNormal));
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

    public static String getStatusMessage(AbstractBuild build, boolean shouldNotifyBackToNormal) {
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
                        && buildHasSucceededBefore && shouldNotifyBackToNormal) {
                    return "back to normal";
                }
                if (result == Result.SUCCESS) {
                    return "success";
                }
                if (result == Result.FAILURE) {
                    return "failure";
                }
                if (result == Result.ABORTED) {
                    return "aborted";
                }
                if (result == Result.NOT_BUILT) {
                    return "not built";
                }
                if (result == Result.UNSTABLE) {
                    return "unstable";
                }
                if (lastNonAbortedBuild != null && previousResult != null && result.isWorseThan(previousResult)) {
                    return "regression";
                }
            }
        }
        return null;
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
