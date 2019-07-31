package com.flock.plugin;

public enum BuildResult {

    SUCCESS, FAILURE, ABORTED, NOT_BUILT, REGRESSION, UNSTABLE, BACK_TO_NORMAL;

    public String stringValue() {
        switch (this) {
            case SUCCESS:
                return "success";
            case FAILURE:
                return "failure";
            case ABORTED:
                return "aborted";
            case NOT_BUILT:
                return "not built";
            case UNSTABLE:
                return "unstable";
            case REGRESSION:
                return "regression";
        }
        return null;
    }
}
