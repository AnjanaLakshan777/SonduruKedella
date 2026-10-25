package com.sondurukedella.reports.dto;

import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;

public record ProjectRefDto(long id, String code, String name, ProjectType type, ProjectStatus status, long clientId, String clientName) {
}
