package com.plataforma.projects.model;

import com.plataforma.projects.exception.ProjectStateException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    private ProjectState state = ProjectState.DRAFT;

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

    @Column(name = "total_tokens", nullable = false, precision = 20, scale = 8)
    private BigDecimal totalTokens; // MINTEO MAXIMO - 200.000 LKN  

    @Column(name = "token_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal tokenPrice; // PRECIO-INICIAL LKN

    @Column(name = "minimum_investment", precision = 15, scale = 4)
    private BigDecimal minimumInvestment; // monto mínimo de inversión por usuario

    /** Monto mínimo total a recaudar. Si no se alcanza antes de softCapDeadline, se dispara reembolso. */
    @Column(name = "soft_cap", precision = 20, scale = 4)
    private BigDecimal softCap;

    /** Monto máximo a recaudar. Una vez alcanzado, la ronda cierra automáticamente. */
    @Column(name = "hard_cap", precision = 20, scale = 4)
    private BigDecimal hardCap;

    /** Fecha límite para alcanzar el soft cap. Si no se alcanza, se dispara devolución. */
    @Column(name = "soft_cap_deadline")
    private LocalDate softCapDeadline;

    /** Fecha esperada de inicio de operaciones (apertura del parque). */
    @Column(name = "expected_open_date")
    private LocalDate expectedOpenDate;

    /** Monto total recaudado hasta ahora. Se actualiza vía eventos de inversión. */
    @Column(name = "raised_amount", precision = 20, scale = 4)
    @Builder.Default
    private BigDecimal raisedAmount = BigDecimal.ZERO;

    @Column(name = "expected_annual_yield", precision = 5, scale = 2)
    private BigDecimal expectedAnnualYield;

    /** Cantidad anual estimada a producir, en MWh. Es información para el inversor — no se usa para calcular dividendos. */
    @Column(name = "expected_annual_production_mwh", precision = 18, scale = 4)
    private BigDecimal expectedAnnualProductionMWh;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectMetric> metrics = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectDocument> documents = new ArrayList<>();

    // ── Lógica de negocio ────────────────────────────────────────────────────

    public boolean canAdvanceTo(ProjectState target) {
        // Estados finales: no se puede salir de ellos
        if (this.state == ProjectState.CLOSED || this.state == ProjectState.CANCELLED) {
            return false;
        }
        // Cancelación permitida desde cualquier estado no-final
        if (target == ProjectState.CANCELLED) {
            return true;
        }
        // Progresión normal secuencial
        return switch (this.state) {
            case DRAFT    -> target == ProjectState.PRE_OPEN;
            case PRE_OPEN -> target == ProjectState.OPEN;
            case OPEN     -> target == ProjectState.CLOSED;
            default       -> false;
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

    public boolean canReceiveInvestments() {
        return this.state == ProjectState.OPEN;
    }
}
