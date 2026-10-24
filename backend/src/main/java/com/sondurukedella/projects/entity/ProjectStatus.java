package com.sondurukedella.projects.entity;

public enum ProjectStatus {
    PLANNING("Planning"),
    IN_PROGRESS("In Progress"),
    ON_HOLD("On Hold"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    // the wording the UI uses, so activity messages read the same as the old local feed
    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // You can't create a project that is already finished or cancelled - the form doesn't offer it.
    public boolean allowedAtCreation() {
        return this != COMPLETED && this != CANCELLED;
    }
}
