package org.acme.domain;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ReadingProcessor {
    @ConfigProperty(name = "weighing.stable-samples", defaultValue = "20")
    int requiredSamples;

    @ConfigProperty(name = "weighing.tolerance-kg", defaultValue = "2.0")
    BigDecimal toleranceKg;

    @PersistenceContext
    EntityManager entityManager;

    @Incoming("scale-readings-in")
    @Blocking
    @Transactional
    public void process(ScaleResource.Reading reading) {
        if (ProcessedReading.count("readingId", reading.readingId()) > 0) {
            return;
        }
        ProcessedReading processed = new ProcessedReading();
        processed.readingId = reading.readingId();
        processed.persist();

        WeighingScale scale = WeighingScale.find("externalId = ?1 and active = true", reading.id())
                .firstResult();
        if (scale == null) {
            return;
        }

        ScaleState state = ScaleState.find("scalePlateKey = ?1", reading.id()).firstResult();
        if (state == null) {
            state = newState(reading);
            state.persist();
            return;
        }
        if (!state.plate.equals(reading.plate())
                || state.lastSeen != null && reading.timestamp().isBefore(state.lastSeen)) {
            resetState(state, reading);
            return;
        }

        BigDecimal nextMin = state.minKg.min(reading.weight());
        BigDecimal nextMax = state.maxKg.max(reading.weight());
        if (nextMax.subtract(nextMin).compareTo(toleranceKg) > 0) {
            resetState(state, reading);
            return;
        }

        state.stableSamples++;
        state.minKg = nextMin;
        state.maxKg = nextMax;
        state.meanKg = state.meanKg.multiply(BigDecimal.valueOf(state.stableSamples - 1))
                .add(reading.weight())
                .divide(BigDecimal.valueOf(state.stableSamples), 3, RoundingMode.HALF_UP);
        state.lastSeen = reading.timestamp();
        if (state.stableSamples < requiredSamples) {
            return;
        }

        Truck truck = Truck.find("upper(plate) = ?1 and active = true", reading.plate())
                .firstResult();
        if (truck == null) {
            state.stableSamples = 0;
            return;
        }
        Transport transport = Transport.find(
                "truck = ?1 and branch = ?2 and status in ?3",
                truck,
                scale.branch,
                List.of(TransportStatus.STARTED, TransportStatus.AWAITING_WEIGHING))
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (transport == null) {
            state.stableSamples = 0;
            return;
        }

        BigDecimal netKg = state.meanKg.subtract(truck.tareKg);
        if (netKg.signum() <= 0) {
            state.stableSamples = 0;
            return;
        }

        String idempotencyKey = "transport:" + transport.id;
        if (Weighing.count("idempotencyKey", idempotencyKey) > 0) {
            state.stableSamples = 0;
            return;
        }

        Inventory inventory = Inventory.find("branch = ?1 and grain = ?2", scale.branch, transport.grain)
                .firstResult();
        BigDecimal availableKg = BigDecimal.ZERO;
        if (inventory != null) {
            entityManager.refresh(inventory, LockModeType.PESSIMISTIC_WRITE);
            availableKg = inventory.availableKg;
        }
        BigDecimal margin = PricingPolicy.marginForAvailableStock(availableKg);
        BigDecimal cargoCost = transport.purchasePricePerTon.multiply(netKg)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        Weighing weighing = new Weighing();
        weighing.idempotencyKey = idempotencyKey;
        weighing.plate = reading.plate();
        weighing.grossKg = state.meanKg;
        weighing.tareKg = truck.tareKg;
        weighing.netKg = netKg;
        weighing.purchasePricePerTon = transport.purchasePricePerTon;
        weighing.cargoCost = cargoCost;
        weighing.margin = margin;
        weighing.estimatedSalePrice = PricingPolicy.salePrice(cargoCost, margin);
        weighing.scale = scale;
        weighing.grain = transport.grain;
        weighing.transport = transport;
        weighing.weighedAt = reading.timestamp();
        weighing.persist();

        updateInventory(inventory, scale, transport.grain, netKg);
        transport.status = TransportStatus.COMPLETED;
        transport.finishedAt = reading.timestamp();
        state.stableSamples = 0;
    }

    private ScaleState newState(ScaleResource.Reading reading) {
        ScaleState state = new ScaleState();
        state.scalePlateKey = reading.id();
        state.plate = reading.plate();
        state.minKg = reading.weight();
        state.maxKg = reading.weight();
        state.meanKg = reading.weight();
        state.stableSamples = 1;
        state.lastSeen = reading.timestamp();
        return state;
    }

    private void resetState(ScaleState state, ScaleResource.Reading reading) {
        state.plate = reading.plate();
        state.minKg = reading.weight();
        state.maxKg = reading.weight();
        state.meanKg = reading.weight();
        state.stableSamples = 1;
        state.lastSeen = reading.timestamp();
    }

    private void updateInventory(
            Inventory inventory,
            WeighingScale scale,
            GrainType grain,
            BigDecimal netKg) {
        if (inventory != null) {
            inventory.availableKg = inventory.availableKg.add(netKg);
            return;
        }
        entityManager.createNativeQuery("""
                INSERT INTO inventory (branch_id, grain_id, available_kg)
                VALUES (:branchId, :grainId, :netKg)
                ON CONFLICT (branch_id, grain_id)
                DO UPDATE SET available_kg = inventory.available_kg + EXCLUDED.available_kg
                """)
                .setParameter("branchId", scale.branch.id)
                .setParameter("grainId", grain.id)
                .setParameter("netKg", netKg)
                .executeUpdate();
    }
}
