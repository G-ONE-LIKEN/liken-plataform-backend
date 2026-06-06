package com.plataforma.blockchain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPublicationFailureRequest {
    private Long projectId;
    private String deployTxHash;
    private String errorMessage;
}
