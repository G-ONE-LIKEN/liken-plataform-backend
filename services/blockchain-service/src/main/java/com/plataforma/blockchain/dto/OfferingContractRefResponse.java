package com.plataforma.blockchain.dto;

import lombok.Data;

@Data
public class OfferingContractRefResponse {
    private Long projectId;
    private Long registryProjectId;
    private String offeringContractAddress;
}
