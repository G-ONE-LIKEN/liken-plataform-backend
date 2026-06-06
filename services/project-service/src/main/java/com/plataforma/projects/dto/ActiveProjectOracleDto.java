package com.plataforma.projects.dto;

import com.plataforma.projects.model.Project;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ActiveProjectOracleDto {

    private Long id;
    private String name;
    private BigDecimal installedCapacityMw;

    public static ActiveProjectOracleDto from(Project project) {
        return ActiveProjectOracleDto.builder()
                .id(project.getId())
                .name(project.getName())
                .installedCapacityMw(project.getInstalledCapacityMW())
                .build();
    }
}