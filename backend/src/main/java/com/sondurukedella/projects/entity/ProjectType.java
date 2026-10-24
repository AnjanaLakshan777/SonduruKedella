package com.sondurukedella.projects.entity;

// Fixed once the project exists - commission, delay terms and stock issues all hang off it.
public enum ProjectType {
    DESIGN("Design"),
    CONSTRUCTION("Construction"),
    CONSULTATION("Consultation");

    private final String label;

    ProjectType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
