package com.plataforma.blockchain.indexer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexerCheckpointRepository extends JpaRepository<IndexerCheckpoint, String> {
}
