package com.plataforma.projects.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPublicationSuccessRequest {
    private Long projectId;
    private Long registryProjectId;
    private String offeringContractAddress;
    private String deployTxHash;
    private Long deployBlockNumber;
}
