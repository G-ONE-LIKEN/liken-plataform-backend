package com.plataforma.blockchain.indexer;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Estado del indexer por contrato. Permite reanudar tras un restart sin
 * re-procesar eventos. La clave es la direccion del contrato (un unico proyecto
 * por OfferingContract; un unico global para LinkenToken/Registry/Distributor).
 */
@Entity
@Table(name = "indexer_checkpoint")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexerCheckpoint {

    @Id
    @Column(name = "contract_address", length = 42)
    private String contractAddress;

    @Column(name = "last_processed_block", nullable = false)
    private Long lastProcessedBlock;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
