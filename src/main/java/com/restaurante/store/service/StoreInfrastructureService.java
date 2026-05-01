package com.restaurante.store.service;

import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreInfrastructureService {

    public static final String FULFILLMENT_NAME = "LOJA_SAGRADA";

    private final CozinhaRepository cozinhaRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    public StoreInfrastructureService(CozinhaRepository cozinhaRepository,
                                      UnidadeAtendimentoRepository unidadeAtendimentoRepository) {
        this.cozinhaRepository = cozinhaRepository;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
    }

    @Transactional
    public FulfillmentUnit ensureFulfillmentUnit() {
        UnidadeAtendimento unidade = unidadeAtendimentoRepository.findByNomeIgnoreCase(FULFILLMENT_NAME)
                .orElseGet(() -> unidadeAtendimentoRepository.save(UnidadeAtendimento.builder()
                        .nome(FULFILLMENT_NAME)
                        .tipo(TipoUnidadeAtendimento.EVENTO)
                        .ativa(true)
                        .descricao("Unidade interna de atendimento da Loja GDSE")
                        .build()));

        Cozinha fulfillment = cozinhaRepository.findByNomeIgnoreCase(FULFILLMENT_NAME)
                .orElseGet(() -> cozinhaRepository.save(Cozinha.builder()
                        .nome(FULFILLMENT_NAME)
                        .tipo(storeFulfillmentTipo())
                        .ativa(true)
                        .descricao("Unidade interna de separação da Loja GDSE")
                        .build()));

        if (!unidade.getCozinhas().contains(fulfillment)) {
            unidade.adicionarCozinha(fulfillment);
            unidade = unidadeAtendimentoRepository.save(unidade);
        }

        return new FulfillmentUnit(unidade, fulfillment);
    }

    public TipoCozinha storeFulfillmentTipo() {
        return TipoCozinha.ESPECIAL;
    }

    public record FulfillmentUnit(UnidadeAtendimento unidadeAtendimento, Cozinha fulfillment) {}
}
