package com.plataforma.marketplace.repository;

import com.plataforma.marketplace.model.Order;
import com.plataforma.marketplace.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** ordenes activas de todos los proyectos, mas recientes primero. */
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /** ordenes activas de un proyecto, ordenadas por precio (mas barato primero). */
    Page<Order> findByStatusAndProjectIdOrderByPricePerTokenAsc(
            OrderStatus status, Long projectId, Pageable pageable);

    /** Todas las ordenes del vendedor (cualquier estado). */
    Page<Order> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    /**
     * Busca una orden OPEN por id con lock pesimista (SELECT FOR UPDATE).
     * Usado en el matching para evitar double-match concurrente.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.status = :status")
    Optional<Order> findByIdAndStatusForUpdate(Long id, OrderStatus status);

    /** ordenes OPEN que ya vencieron — para el job de expiracion. */
    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime now);

    /** ordenes OPEN de un proyecto — para cancelar masivamente al cambiar estado. */
    List<Order> findByStatusAndProjectId(OrderStatus status, Long projectId);
}
