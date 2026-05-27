-- Prompt 49: Delivery readiness / fulfillment / courier network (MVP)

CREATE TABLE IF NOT EXISTS tenant_delivery_policies (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_mode VARCHAR(40) NOT NULL,
    accepts_consuma_network BOOLEAN NOT NULL DEFAULT FALSE,
    accepts_tenant_own_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    allow_customer_pickup BOOLEAN NOT NULL DEFAULT TRUE,
    require_payment_before_delivery BOOLEAN NOT NULL DEFAULT TRUE,
    auto_create_delivery_job_after_payment BOOLEAN NOT NULL DEFAULT FALSE,
    max_delivery_distance_km NUMERIC(9, 3),
    preparation_time_minutes INTEGER,
    cancel_allowed_until_status VARCHAR(60) NOT NULL,
    delivery_notes TEXT,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6),
    version BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_delivery_policy_tenant ON tenant_delivery_policies (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_delivery_policy_status ON tenant_delivery_policies (tenant_id, status);

ALTER TABLE tenant_delivery_policies ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE tenant_delivery_policies ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS product_delivery_policies (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    product_id BIGINT NOT NULL REFERENCES produtos(id),
    delivery_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    fragile BOOLEAN NOT NULL DEFAULT FALSE,
    requires_cooling BOOLEAN NOT NULL DEFAULT FALSE,
    max_delivery_distance_km NUMERIC(9, 3),
    estimated_package_weight NUMERIC(9, 3),
    package_size VARCHAR(40),
    allow_motorbike_delivery BOOLEAN NOT NULL DEFAULT TRUE,
    allow_car_delivery BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6),
    version BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_delivery_policy_key ON product_delivery_policies (tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_product_delivery_policy_status ON product_delivery_policies (tenant_id, status);

ALTER TABLE product_delivery_policies ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE product_delivery_policies ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS order_fulfillments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id),
    fulfillment_type VARCHAR(60) NOT NULL,
    status VARCHAR(60) NOT NULL,
    customer_name VARCHAR(160),
    customer_phone_masked VARCHAR(40),
    delivery_address_text TEXT,
    delivery_latitude NUMERIC(10, 6),
    delivery_longitude NUMERIC(10, 6),
    delivery_notes TEXT,
    delivery_fee_amount NUMERIC(19, 2),
    delivery_distance_km NUMERIC(9, 3),
    delivery_requested_at TIMESTAMP(6),
    pickup_ready_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    cancelled_at TIMESTAMP(6),
    cancellation_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6),
    version BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_order_fulfillment_pedido ON order_fulfillments (tenant_id, pedido_id);
CREATE INDEX IF NOT EXISTS idx_order_fulfillment_status ON order_fulfillments (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_order_fulfillment_type ON order_fulfillments (tenant_id, fulfillment_type);

ALTER TABLE order_fulfillments ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE order_fulfillments ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS courier_profiles (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT REFERENCES tenants(id),
    courier_user_id BIGINT REFERENCES users(id),
    courier_code VARCHAR(40) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone_masked VARCHAR(40),
    status VARCHAR(40) NOT NULL,
    verification_status VARCHAR(40) NOT NULL,
    vehicle_type VARCHAR(40) NOT NULL,
    vehicle_plate_masked VARCHAR(60),
    has_own_vehicle BOOLEAN NOT NULL DEFAULT TRUE,
    accepts_terms BOOLEAN NOT NULL DEFAULT FALSE,
    current_availability VARCHAR(40) NOT NULL,
    current_latitude NUMERIC(10, 6),
    current_longitude NUMERIC(10, 6),
    last_location_update_at TIMESTAMP(6),
    active_delivery_job_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6),
    version BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_courier_code ON courier_profiles (courier_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_courier_user ON courier_profiles (courier_user_id);
CREATE INDEX IF NOT EXISTS idx_courier_status ON courier_profiles (status, verification_status, current_availability);
CREATE INDEX IF NOT EXISTS idx_courier_location_time ON courier_profiles (last_location_update_at);

ALTER TABLE courier_profiles ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE courier_profiles ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS delivery_jobs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id),
    order_fulfillment_id BIGINT NOT NULL REFERENCES order_fulfillments(id),
    courier_id BIGINT REFERENCES courier_profiles(id),
    status VARCHAR(80) NOT NULL,
    pickup_address_text TEXT,
    pickup_latitude NUMERIC(10, 6),
    pickup_longitude NUMERIC(10, 6),
    delivery_address_text TEXT,
    delivery_latitude NUMERIC(10, 6),
    delivery_longitude NUMERIC(10, 6),
    estimated_distance_km NUMERIC(9, 3),
    estimated_delivery_fee NUMERIC(19, 2),
    final_delivery_fee NUMERIC(19, 2),
    delivery_fee_currency VARCHAR(10),
    delivery_fee_payment_status VARCHAR(40) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    assigned_at TIMESTAMP(6),
    accepted_at TIMESTAMP(6),
    rejected_at TIMESTAMP(6),
    picked_up_at TIMESTAMP(6),
    delivered_at TIMESTAMP(6),
    cancelled_at TIMESTAMP(6),
    failed_at TIMESTAMP(6),
    cancellation_reason TEXT,
    failure_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6),
    version BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_job_pedido ON delivery_jobs (tenant_id, pedido_id);
CREATE INDEX IF NOT EXISTS idx_delivery_job_status ON delivery_jobs (tenant_id, status, requested_at);
CREATE INDEX IF NOT EXISTS idx_delivery_job_courier ON delivery_jobs (courier_id, status);

ALTER TABLE delivery_jobs ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE delivery_jobs ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS delivery_courier_invites (
    id BIGSERIAL PRIMARY KEY,
    delivery_job_id BIGINT NOT NULL REFERENCES delivery_jobs(id),
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    courier_id BIGINT NOT NULL REFERENCES courier_profiles(id),
    status VARCHAR(40) NOT NULL,
    distance_to_pickup_km NUMERIC(9, 3),
    invited_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    expires_at TIMESTAMP(6),
    responded_at TIMESTAMP(6),
    rejection_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    updated_at TIMESTAMP(6),
    version BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_delivery_invite_active ON delivery_courier_invites (delivery_job_id, courier_id, status);
CREATE INDEX IF NOT EXISTS idx_delivery_invite_job_status ON delivery_courier_invites (tenant_id, delivery_job_id, status);
CREATE INDEX IF NOT EXISTS idx_delivery_invite_courier_status ON delivery_courier_invites (tenant_id, courier_id, status);

ALTER TABLE delivery_courier_invites ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE delivery_courier_invites ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);

CREATE TABLE IF NOT EXISTS courier_location_pings (
    id BIGSERIAL PRIMARY KEY,
    courier_id BIGINT NOT NULL REFERENCES courier_profiles(id),
    latitude NUMERIC(10, 6) NOT NULL,
    longitude NUMERIC(10, 6) NOT NULL,
    accuracy_meters NUMERIC(9, 3),
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT now(),
    source VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_courier_ping_courier_time ON courier_location_pings (courier_id, recorded_at);

ALTER TABLE courier_location_pings ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6);
ALTER TABLE courier_location_pings ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE courier_location_pings ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100);
ALTER TABLE courier_location_pings ADD COLUMN IF NOT EXISTS version BIGINT;
