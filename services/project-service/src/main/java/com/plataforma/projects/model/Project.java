package com.plataforma.projects.model;

import com.plataforma.projects.exception.ProjectStateException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Proyecto energético tokenizable.
 *
 * <p>Bajo el modelo de <b>token global</b> (LKN unico para toda la plataforma,
 * ver ADR-0011), este entity NO representa un token distinto por proyecto. Lo
 * que representa es: un parque de generacion, la ronda primaria de venta de LKN
 * asociada a él, y las addresses on-chain de su {@code OfferingContract}.
 *
 * <p>Identificadores on-chain (poblados por el Blockchain Service tras el deploy):
 * <ul>
 *   <li>{@link #registryProjectId}: ID que devuelve {@code ProjectRegistry.registerProject}.</li>
 *   <li>{@link #offeringContractAddress}: direccion del OfferingContract desplegado.</li>
 *   <li>{@link #deployTxHash} / {@link #deployBlockNumber}: trazabilidad del deploy.</li>
 *   <li>{@link #onChainStatus}: estado del deploy (NOT_DEPLOYED por default).</li>
 *   <li>{@link #roundState}: estado on-chain de la ronda (refleja OfferingContract.state).</li>
 * </ul>
 *
 * <p>Las addresses de los contratos <b>globales</b> ({@code LinkenToken},
 * {@code DividendDistributor}, {@code USDC}) NO viven aca — son configuracion
 * de plataforma en {@code application.yml}.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectState state = ProjectState.PENDING_APPROVAL;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "energy_type", nullable = false)
    private EnergyType energyType;

    private String province;

    @Builder.Default
    private String country = "Argentina";

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "installed_capacity_mw", precision = 15, scale = 4)
    private BigDecimal installedCapacityMW;

    /**
     * Cantidad de LKN que el emisor deposita en escrow del OfferingContract para
     * esta ronda. NO es un cap de mint (el supply de LKN es global y fijo desde el TGE).
     * Es el tramo asignado a este proyecto para la venta primaria.
     */
    @Column(name = "total_tokens", nullable = false, precision = 20, scale = 8)
    private BigDecimal totalTokens;

    /**
     * Precio en USDC por LKN durante la etapa {@code FUNDING} (ronda abierta).
     * 4 decimales en BD (ej. 8.0000 = $8). El backend convierte a 6 decimales (USDC)
     * cuando habla con la chain (ver adapter de unidades del Blockchain Service).
     * Debe ser estrictamente menor que {@link #standardPrice}.
     */
    @Column(name = "early_bird_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal earlyBirdPrice;

    /**
     * Precio en USDC por LKN una vez que la ronda cerro exitosamente y el proyecto
     * paso a estado operativo (Registry {@code ACTIVE}). Debe ser mayor que
     * {@link #earlyBirdPrice}.
     */
    @Column(name = "standard_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal standardPrice;

    @Column(name = "minimum_investment", precision = 15, scale = 4)
    private BigDecimal minimumInvestment; // monto minimo de inversion por usuario

    /**
     * Monto minimo total a recaudar. Si no se alcanza antes de {@link #expectedOpenDate},
     * el contrato dispara {@code RoundFailed} y los inversores reclaman refund
     * individualmente (pull).
     */
    @Column(name = "soft_cap", precision = 20, scale = 4)
    private BigDecimal softCap;

    /** Monto maximo a recaudar. Una vez alcanzado, la ronda cierra automaticamente. */
    @Column(name = "hard_cap", precision = 20, scale = 4)
    private BigDecimal hardCap;

    /**
     * Fecha esperada de apertura del parque — unico campo de fecha clave.
     * Se usa como {@code OfferingContract.deadline} al deployar el contrato on-chain:
     * <ul>
     *   <li>Si en esta fecha se alcanzo el {@link #softCap} → {@code RoundFinalized}
     *       → backend pasa de PRE_OPEN a OPEN.</li>
     *   <li>Si no se alcanzo → {@code RoundFailed} → backend pasa a CANCELLED.</li>
     * </ul>
     */
    @Column(name = "expected_open_date")
    private LocalDate expectedOpenDate;

    /** Monto total recaudado hasta ahora. Se actualiza via eventos de inversion. */
    @Column(name = "raised_amount", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal raisedAmount = BigDecimal.ZERO;

    /** LKN vendidos en la ronda primaria, segun eventos TokensPurchased. */
    @Column(name = "total_tokens_sold", precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal totalTokensSold = BigDecimal.ZERO;

    @Column(name = "expected_annual_yield", precision = 5, scale = 2)
    private BigDecimal expectedAnnualYield;

    /** Cantidad anual estimada a producir, en MWh. Es informacion para el inversor — no se usa para calcular dividendos. */
    @Column(name = "expected_annual_production_mwh", precision = 18, scale = 4)
    private BigDecimal expectedAnnualProductionMWh;

    @Builder.Default
    private Boolean active = true;

    // ── Identificadores on-chain ──────────────────────────────────────────────

    /** ID que devuelve {@code ProjectRegistry.registerProject}. Lo setea el Blockchain Service. */
    @Column(name = "registry_project_id")
    private Long registryProjectId;

    /** Direccion del {@code OfferingContract} desplegado para esta ronda (EIP-55). */
    @Column(name = "offering_contract_address", length = 42)
    private String offeringContractAddress;

    /** Hash de la tx de deploy del OfferingContract. */
    @Column(name = "deploy_tx_hash", length = 66)
    private String deployTxHash;

    /** Numero de bloque del deploy del OfferingContract. */
    @Column(name = "deploy_block_number")
    private Long deployBlockNumber;

    /** Estado del deploy on-chain. Default: NOT_DEPLOYED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "on_chain_status", nullable = false, length = 20)
    @Builder.Default
    private OnChainStatus onChainStatus = OnChainStatus.NOT_DEPLOYED;

    /** Estado on-chain de la ronda primaria (refleja {@code OfferingContract.state}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "round_state", length = 20)
    private RoundState roundState;

    // ── Relaciones ────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectMetric> metrics = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectDocument> documents = new ArrayList<>();

    // ── Logica de negocio ────────────────────────────────────────────────────

    public boolean canAdvanceTo(ProjectState target) {
        // Estados finales: no se puede salir de ellos
        if (this.state == ProjectState.CLOSED || this.state == ProjectState.CANCELLED) {
            return false;
        }
        // Cancelacion permitida desde cualquier estado no-final
        if (target == ProjectState.CANCELLED) {
            return true;
        }
        // Progresion normal secuencial
        return switch (this.state) {
            case PENDING_APPROVAL -> target == ProjectState.DRAFT;
            case DRAFT            -> target == ProjectState.PRE_OPEN;
            case PRE_OPEN         -> target == ProjectState.OPEN;
            case OPEN             -> target == ProjectState.CLOSED;
            default               -> false;
        };
    }

    public void advanceState(ProjectState target) {
        if (!canAdvanceTo(target)) {
            throw new ProjectStateException(
                String.format("No se puede pasar de %s a %s", this.state, target)
            );
        }
        this.state = target;
    }

    /**
     * Acepta compra en {@link ProjectState#PRE_OPEN} (ronda primaria, precio earlyBird)
     * y en {@link ProjectState#OPEN} (parque operativo, precio standard). La diferencia
     * esta en el precio que devuelve {@link #currentPrice()}.
     */
    public boolean canReceiveInvestments() {
        return this.state == ProjectState.PRE_OPEN || this.state == ProjectState.OPEN;
    }

    /**
     * Precio vigente para el frontend, en funcion del estado del proyecto.
     * Refleja el comportamiento de {@code ProjectRegistry.currentPrice(projectId)}:
     * <ul>
     *   <li>{@code PRE_OPEN}  → {@code earlyBirdPrice} (Registry FUNDING).</li>
     *   <li>{@code OPEN}      → {@code standardPrice} (Registry ACTIVE).</li>
     *   <li>otros            → earlyBird como referencia (pre-chain o historico).</li>
     * </ul>
     */
    public BigDecimal currentPrice() {
        return switch (this.state) {
            case PRE_OPEN -> this.earlyBirdPrice;
            case OPEN -> this.standardPrice;
            default -> this.earlyBirdPrice;
        };
    }
}
