package com.sondurukedella.projects.mapper;

import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project, String clientName) {
        return new ProjectResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getType(),
                project.getClientId(),
                clientName,
                project.getLocation(),
                project.getDescription(),
                project.getContractValue(),
                project.getStartDate(),
                project.getExpectedCompletion(),
                project.getActualCompletion(),
                project.getStatus(),
                project.getNotes(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion());
    }
}
