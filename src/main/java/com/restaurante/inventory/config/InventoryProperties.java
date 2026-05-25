package com.restaurante.inventory.config;

import com.restaurante.model.enums.InventoryConsumptionTriggerType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.RoundingMode;

@Data
@Configuration
@ConfigurationProperties(prefix = "consuma.inventory")
public class InventoryProperties {

    private boolean enabled = true;

    private Consumption consumption = new Consumption();
    private Math math = new Math();

    @Data
    public static class Consumption {
        private InventoryConsumptionTriggerType trigger = InventoryConsumptionTriggerType.PAYMENT_CONFIRMED;
    }

    @Data
    public static class Math {
        private int monetaryScale = 2;
        private int quantityScale = 6;
        private int calculationScale = 8;
        private RoundingMode roundingMode = RoundingMode.HALF_UP;
    }
}
