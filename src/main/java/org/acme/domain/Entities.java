package org.acme.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

public final class Entities {
    private Entities() {
    }
}

@Entity
@Table(name = "branch")
class Branch extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public boolean active = true;
}

@Entity
@Table(name = "truck")
class Truck extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String plate;

    @Column(name = "tare_kg", nullable = false, precision = 14, scale = 3)
    public BigDecimal tareKg;

    @Column(nullable = false)
    public boolean active = true;
}

@Entity
@Table(name = "grain_type")
class GrainType extends PanacheEntity {
    @Column(nullable = false, unique = true)
    public String name;

    @Column(name = "purchase_price_per_ton", nullable = false, precision = 14, scale = 4)
    public BigDecimal purchasePricePerTon;

    @Column(nullable = false)
    public boolean active = true;
}

@Entity
@Table(name = "weighing_scale")
class WeighingScale extends PanacheEntity {
    @Column(name = "external_id", nullable = false, unique = true)
    public String externalId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id")
    public Branch branch;

    @Column(name = "credential_hash", nullable = false)
    public String credentialHash;

    @Column(nullable = false)
    public boolean active = true;
}

@Entity
@Table(name = "transport", indexes = @Index(columnList = "truck_id,status"))
class Transport extends PanacheEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "truck_id")
    public Truck truck;

    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id")
    public Branch branch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grain_id")
    public GrainType grain;

    @Column(name = "purchase_price_per_ton", nullable = false, precision = 14, scale = 4)
    public BigDecimal purchasePricePerTon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TransportStatus status = TransportStatus.STARTED;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    public Instant finishedAt;
}

enum TransportStatus {
    STARTED,
    AWAITING_WEIGHING,
    COMPLETED,
    CANCELLED,
    ERROR
}

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "grain_id"}))
class Inventory extends PanacheEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id")
    public Branch branch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grain_id")
    public GrainType grain;

    @Column(name = "available_kg", nullable = false, precision = 14, scale = 3)
    public BigDecimal availableKg = BigDecimal.ZERO;
}

@Entity
@Table(
        name = "weighing",
        uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"),
        indexes = @Index(columnList = "weighed_at"))
class Weighing extends PanacheEntity {
    @Column(name = "idempotency_key", nullable = false)
    public String idempotencyKey;

    @Column(nullable = false)
    public String plate;

    @Column(name = "gross_kg", nullable = false, precision = 14, scale = 3)
    public BigDecimal grossKg;

    @Column(name = "tare_kg", nullable = false, precision = 14, scale = 3)
    public BigDecimal tareKg;

    @Column(name = "net_kg", nullable = false, precision = 14, scale = 3)
    public BigDecimal netKg;

    @Column(name = "purchase_price_per_ton", nullable = false, precision = 14, scale = 4)
    public BigDecimal purchasePricePerTon;

    @Column(name = "cargo_cost", nullable = false, precision = 14, scale = 2)
    public BigDecimal cargoCost;

    @Column(nullable = false, precision = 5, scale = 4)
    public BigDecimal margin;

    @Column(name = "estimated_sale_price", nullable = false, precision = 14, scale = 2)
    public BigDecimal estimatedSalePrice;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scale_id")
    public WeighingScale scale;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grain_id")
    public GrainType grain;

    @ManyToOne(optional = false)
    @JoinColumn(name = "transport_id")
    public Transport transport;

    @Column(name = "weighed_at", nullable = false)
    public Instant weighedAt = Instant.now();
}

@Entity
@Table(name = "scale_state")
class ScaleState extends PanacheEntity {
    @Column(name = "scale_plate_key", nullable = false, unique = true)
    public String scalePlateKey;

    @Column(nullable = false)
    public String plate;

    @Column(name = "stable_samples", nullable = false)
    public long stableSamples;

    @Column(name = "mean_kg", nullable = false, precision = 14, scale = 3)
    public BigDecimal meanKg = BigDecimal.ZERO;

    @Column(name = "min_kg", nullable = false)
    public BigDecimal minKg = BigDecimal.ZERO;

    @Column(name = "max_kg", nullable = false)
    public BigDecimal maxKg = BigDecimal.ZERO;

    @Column(name = "last_seen")
    public Instant lastSeen;
}

@Entity
@Table(name = "processed_reading")
class ProcessedReading extends PanacheEntity {
    @Column(name = "reading_id", nullable = false, unique = true)
    public String readingId;

    @Column(name = "processed_at", nullable = false)
    public Instant processedAt = Instant.now();
}
