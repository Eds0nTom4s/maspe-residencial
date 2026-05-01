package com.restaurante.store.analytics;

import com.restaurante.model.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "store_analytics_events", indexes = {
        @Index(name = "idx_store_analytics_event_type", columnList = "event_type"),
        @Index(name = "idx_store_analytics_socio", columnList = "socio_id"),
        @Index(name = "idx_store_analytics_timestamp", columnList = "timestamp")
})
public class StoreAnalyticsEvent extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "socio_id", length = 100)
    private String socioId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    public StoreAnalyticsEvent() {}

    public StoreAnalyticsEvent(String eventType, String socioId, Long productId, Long orderId, String metadata) {
        this.eventType = eventType;
        this.socioId = socioId;
        this.productId = productId;
        this.orderId = orderId;
        this.metadata = metadata;
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSocioId() { return socioId; }
    public void setSocioId(String socioId) { this.socioId = socioId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
