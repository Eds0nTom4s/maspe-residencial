package com.restaurante.service.operacional;

import com.restaurante.exception.OrderInvalidStateTransitionException;
import com.restaurante.exception.OrderNotAcceptedForProductionException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PublicOrderStateMachineService {

    public static final String CODE_ORDER_INVALID_STATE_TRANSITION = "ORDER_INVALID_STATE_TRANSITION";
    public static final String CODE_ORDER_NOT_ACCEPTED_FOR_PRODUCTION = "ORDER_NOT_ACCEPTED_FOR_PRODUCTION";

    public void assertPedidoPodeSerAceite(Pedido pedido) {
        if (pedido == null) {
            throw new OrderInvalidStateTransitionException(
                    "Este pedido não pode transitar para o estado solicitado.",
                    CODE_ORDER_INVALID_STATE_TRANSITION,
                    null,
                    "ACEITAR"
            );
        }

        StatusPedido status = pedido.getStatus();
        if (status == StatusPedido.CANCELADO || status == StatusPedido.FINALIZADO) {
            throw new OrderInvalidStateTransitionException(
                    "Este pedido não pode transitar para o estado solicitado.",
                    CODE_ORDER_INVALID_STATE_TRANSITION,
                    status,
                    "ACEITAR"
            );
        }
    }

    public void assertSubPedidoPodeEntrarNaProducao(SubPedido subPedido) {
        if (subPedido == null || subPedido.getStatus() == null || subPedido.getStatus() == StatusSubPedido.CRIADO) {
            throw new OrderNotAcceptedForProductionException(
                    "Este pedido ainda não foi aceite para produção.",
                    CODE_ORDER_NOT_ACCEPTED_FOR_PRODUCTION,
                    subPedido != null ? subPedido.getStatus() : null
            );
        }
    }

    public boolean isEligibleForKds(SubPedido subPedido) {
        return subPedido != null && subPedido.getStatus() != null && subPedido.getStatus() != StatusSubPedido.CRIADO;
    }

    public TrackingSnapshot snapshot(Pedido pedido) {
        StatusPedido operationalStatus = pedido != null ? pedido.getStatus() : null;
        StatusFinanceiroPedido paymentStatus = pedido != null ? pedido.getStatusFinanceiro() : null;
        String currentStep = resolveCurrentStep(pedido);
        boolean isFinal = operationalStatus == StatusPedido.FINALIZADO || operationalStatus == StatusPedido.CANCELADO;
        boolean isProblem = operationalStatus == StatusPedido.CANCELADO;

        return new TrackingSnapshot(
                operationalStatus,
                paymentStatus,
                currentStep,
                isFinal,
                isProblem
        );
    }

    private String resolveCurrentStep(Pedido pedido) {
        if (pedido == null) {
            return "UNKNOWN";
        }
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return "CANCELLED";
        }

        List<SubPedido> subPedidos = pedido.getSubPedidos() != null ? pedido.getSubPedidos() : List.of();
        boolean anyPronto = subPedidos.stream().anyMatch(sp -> sp.getStatus() == StatusSubPedido.PRONTO);
        if (anyPronto) {
            return "READY";
        }
        boolean anyEmPreparacao = subPedidos.stream().anyMatch(sp -> sp.getStatus() == StatusSubPedido.EM_PREPARACAO);
        if (anyEmPreparacao) {
            return "IN_PREPARATION";
        }
        boolean anyAceite = subPedidos.stream().anyMatch(sp -> sp.getStatus() == StatusSubPedido.PENDENTE);
        if (anyAceite) {
            return "ACCEPTED";
        }
        boolean allEntregues = !subPedidos.isEmpty() && subPedidos.stream().allMatch(sp -> sp.getStatus() == StatusSubPedido.ENTREGUE);
        if (allEntregues || pedido.getStatus() == StatusPedido.FINALIZADO) {
            return "DELIVERED";
        }
        return "RECEIVED";
    }

    public record TrackingSnapshot(
            StatusPedido operationalStatus,
            StatusFinanceiroPedido paymentStatus,
            String currentStep,
            boolean isFinal,
            boolean isProblem
    ) {
    }
}
