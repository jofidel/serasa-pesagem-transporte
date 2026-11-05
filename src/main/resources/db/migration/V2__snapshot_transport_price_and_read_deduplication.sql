ALTER TABLE transport ADD COLUMN purchase_price_per_ton NUMERIC(14, 4);
UPDATE transport t
SET purchase_price_per_ton = g.purchase_price_per_ton
FROM grain_type g
WHERE g.id = t.grain_id;
ALTER TABLE transport ALTER COLUMN purchase_price_per_ton SET NOT NULL;
CREATE UNIQUE INDEX uq_transport_one_active_per_truck
    ON transport (truck_id)
    WHERE status IN ('STARTED', 'AWAITING_WEIGHING');

-- Stabilization state is scoped to the scale, not to a plate. This discards only transient samples.
DELETE FROM scale_state;

CREATE SEQUENCE processed_reading_SEQ START WITH 1 INCREMENT BY 50;
CREATE TABLE processed_reading (
    id BIGINT PRIMARY KEY,
    reading_id VARCHAR(255) NOT NULL UNIQUE,
    processed_at TIMESTAMPTZ NOT NULL
);
