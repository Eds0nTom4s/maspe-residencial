package com.restaurante.platform.discovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public final class DiscoveryDtos {

    private DiscoveryDtos() {}

    @Schema(description = "Categoria pública do comerciante")
    public record MerchantCategoryDto(
            @Schema(
                            description = "Identificador público lowercase da categoria",
                            example = "restaurant",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Schema(
                            description = "Nome de apresentação da categoria",
                            example = "Restaurantes",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String name) {}

    @Schema(description = "Disponibilidade operacional; UNKNOWN quando não há horário canónico")
    public record MerchantAvailabilityDto(
            @Schema(
                            allowableValues = {
                                "OPEN", "CLOSING_SOON", "OPENS_AT", "CLOSED", "UNKNOWN"
                            },
                            example = "UNKNOWN",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String status,
            @Schema(description = "Minutos restantes, apenas em CLOSING_SOON")
                    Integer minutesRemaining,
            @Schema(description = "Hora local de abertura, apenas em OPENS_AT", example = "08:00:00")
                    LocalTime opensAt) {}

    public record MerchantAddressDto(
            @Schema(
                            description = "Endereço público resumido; nunca contém morada fiscal privada",
                            example = "Maianga — Luanda",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String displayName) {}

    public record MerchantLocationDto(
            @Schema(description = "Latitude pública quando existir", minimum = "-90", maximum = "90")
                    Double latitude,
            @Schema(description = "Longitude pública quando existir", minimum = "-180", maximum = "180")
                    Double longitude,
            @Schema(description = "Município público", example = "Maianga") String municipalityId,
            @Schema(description = "Distância opcional; ausente em v1 enquanto geografia não for suportada")
                    Integer distanceMeters) {}

    public record MerchantPromotionDto(
            String id, String title, String description, String badge) {}

    public record MerchantContactDto(
            @Schema(description = "Telefone explicitamente público") String phone,
            @Schema(description = "Email explicitamente público") String email) {}

    public record MerchantRatingDto(
            @Schema(description = "Classificação pública", minimum = "0", maximum = "5")
                    Double value,
            @Schema(description = "Quantidade de avaliações", minimum = "0") Integer count) {}

    public record MoneyAmountDto(long minorUnits, String currency) {}

    public record WeeklyScheduleDto(
            Set<DayOfWeek> openDays, LocalTime opensAt, LocalTime closesAt) {}

    @Schema(description = "Card público de comerciante")
    public record MerchantSummaryDto(
            @Schema(
                            description = "merchantId: slug público estável",
                            example = "sabor-maianga",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String id,
            @Schema(example = "Sabor da Maianga", requiredMode = Schema.RequiredMode.REQUIRED)
                    String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MerchantCategoryDto category,
            @Schema(description = "Descrição curta opcional") String shortDescription,
            @Schema(description = "URL de imagem opcional; nunca base64") String imageUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MerchantAvailabilityDto availability,
            @Schema(
                            description = "Valores conhecidos: PICKUP, DELIVERY, DINE_IN, SERVICE; array vazio é válido",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    Set<String> fulfillmentOptions,
            MerchantLocationDto location,
            Integer estimatedPreparationMinutes,
            MerchantRatingDto rating,
            @Schema(description = "Ausente enquanto não houver fonte real")
            Integer popularity,
            MoneyAmountDto minimumOrderAmount,
            MerchantPromotionDto promotion,
            @Schema(description = "Ausente enquanto não houver fonte real")
            Boolean featured,
            @Schema(
                            description = "true somente quando existe produto público activo e disponível em categoria activa",
                            requiredMode = Schema.RequiredMode.REQUIRED)
            boolean catalogAvailable) {}

    public record MerchantSectionDto(
            @Schema(
                            description = "Itens sem duplicação dentro da secção; nunca null",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    List<MerchantSummaryDto> items,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasMore) {}
}
