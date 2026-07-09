package com.restaurante.delivery.service;

import com.restaurante.delivery.dto.request.DeliveryFeeCalculationRequest;
import com.restaurante.delivery.repository.DeliveryFeeQuoteRepository;
import com.restaurante.delivery.repository.OrderFulfillmentRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.delivery.repository.TenantDeliveryPolicyRepository;
import com.restaurante.model.entity.DeliveryFeeQuote;
import com.restaurante.model.entity.OrderFulfillment;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.DeliveryFeeQuoteStatus;
import com.restaurante.model.enums.PackageSize;
import com.restaurante.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class DeliveryFeeQuoteService {

    private final DeliveryFeeQuoteRepository quoteRepository;
    private final DeliveryFeeCalculationService calculationService;
    private final PedidoRepository pedidoRepository;
    private final TenantDeliveryPolicyRepository tenantDeliveryPolicyRepository;

    public DeliveryFeeQuoteService(DeliveryFeeQuoteRepository quoteRepository,
                                   DeliveryFeeCalculationService calculationService,
                                   PedidoRepository pedidoRepository,
                                   TenantDeliveryPolicyRepository tenantDeliveryPolicyRepository) {
        this.quoteRepository = quoteRepository;
        this.calculationService = calculationService;
        this.pedidoRepository = pedidoRepository;
        this.tenantDeliveryPolicyRepository = tenantDeliveryPolicyRepository;
    }

    public DeliveryFeeQuote createQuote(Long tenantId, Long pedidoId, BigDecimal distanceKm,
                                        PackageSize packageSize, Boolean fragile, BigDecimal tenantSubsidyAmount) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new BusinessException("PEDIDO_NOT_FOUND"));

        DeliveryFeeCalculationRequest calcRequest = DeliveryFeeCalculationRequest.builder()
                .tenantId(tenantId)
                .pedidoId(pedidoId)
                .distanceKm(distanceKm)
                .packageSize(packageSize != null ? packageSize : PackageSize.MEDIUM)
                .fragile(fragile != null ? fragile : false)
                .tenantSubsidyAmount(tenantSubsidyAmount != null ? tenantSubsidyAmount : BigDecimal.ZERO)
                .requestedAt(LocalDateTime.now())
                .build();

        DeliveryFeeCalculationService.DeliveryFeeQuoteCalculationResult calcResult = calculationService.calculate(calcRequest);

        DeliveryFeeQuote quote = new DeliveryFeeQuote();
        quote.setTenant(pedido.getTenant());
        quote.setPedido(pedido);
        quote.setPricingPolicy(calcResult.getPolicy());
        quote.setStatus(DeliveryFeeQuoteStatus.QUOTED);
        quote.setDistanceKm(distanceKm);
        quote.setCurrency(calcResult.getPolicy().getCurrency());
        quote.setBaseFeeAmount(calcResult.getBaseFeeAmount());
        quote.setDistanceFeeAmount(calcResult.getDistanceFeeAmount());
        quote.setSurchargeAmount(calcResult.getSurchargeAmount());
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setTenantSubsidyAmount(calcResult.getTenantSubsidyAmount());
        quote.setCustomerPaysAmount(calcResult.getCustomerPaysAmount());
        quote.setFinalDeliveryFeeAmount(calcResult.getFinalFeeAmount());
        quote.setCourierEarningAmount(calcResult.getCourierEarningAmount());
        quote.setConsumaCommissionAmount(calcResult.getConsumaCommissionAmount());
        quote.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        return quoteRepository.save(quote);
    }

    public DeliveryFeeQuote getQuote(Long quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new BusinessException("DELIVERY_FEE_QUOTE_NOT_FOUND"));
    }

    public DeliveryFeeQuote acceptQuote(Long quoteId) {
        DeliveryFeeQuote quote = getQuote(quoteId);

        if (quote.getStatus() == DeliveryFeeQuoteStatus.EXPIRED || quote.getExpiresAt().isBefore(LocalDateTime.now())) {
            quote.setStatus(DeliveryFeeQuoteStatus.EXPIRED);
            quoteRepository.save(quote);
            throw new BusinessException("DELIVERY_FEE_QUOTE_EXPIRED");
        }

        if (quote.getStatus() == DeliveryFeeQuoteStatus.CANCELLED) {
            throw new BusinessException("DELIVERY_FEE_QUOTE_EXPIRED"); // treat as expired/invalid state
        }

        quote.setStatus(DeliveryFeeQuoteStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());
        return quoteRepository.save(quote);
    }
}
