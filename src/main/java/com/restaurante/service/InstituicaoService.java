package com.restaurante.service;

import com.restaurante.model.entity.Instituicao;
import com.restaurante.repository.InstituicaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstituicaoService {

    private final InstituicaoRepository instituicaoRepository;

    public Instituicao getInstituicaoAtiva() {
        return instituicaoRepository.findFirstByAtivaTrue()
                .orElseThrow(() -> new RuntimeException("Nenhuma instituição ativa encontrada no sistema"));
    }

    @Transactional
    public Instituicao atualizarInstituicao(Long id, String nome, String sigla, String nif, String urlLogo) {
        Instituicao instituicao = instituicaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instituição não encontrada: " + id));

        instituicao.setNome(nome);
        instituicao.setSigla(sigla);
        instituicao.setNif(nif);
        instituicao.setUrlLogo(urlLogo);

        return instituicaoRepository.save(instituicao);
    }
}
