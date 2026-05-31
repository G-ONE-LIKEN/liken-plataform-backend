package com.plataforma.projects.dto;

import com.plataforma.projects.model.ProjectState;
import lombok.Data;

@Data
public class StateChangeRequest {
    private ProjectState state;
}
