package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.notificacao.service.NotificacaoService;
import com.restaurante.repository.InstituicaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstituicaoService {

    private final InstituicaoRepository instituicaoRepository;
    private final NotificacaoService notificacaoService;

    @Value("${otp.length:4}")
    private int otpLength;

    @Value("${otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${otp.mock:true}")
    private boolean otpMock;

    public Instituicao getInstituicaoAtiva() {
        return instituicaoRepository.findFirstByAtivaTrue()
                .orElseThrow(() -> new RuntimeException("Nenhuma instituição ativa encontrada no sistema"));
    }

    @Transactional
    public Instituicao atualizarInstituicao(Long id, String nome, String sigla, String nif, String urlLogo, String telefoneAutorizacao) {
        Instituicao instituicao = instituicaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instituição não encontrada: " + id));

        instituicao.setNome(nome);
        instituicao.setSigla(sigla);
        instituicao.setNif(nif);
        instituicao.setUrlLogo(urlLogo);
        if (telefoneAutorizacao != null && !telefoneAutorizacao.isBlank()) {
            instituicao.setTelefoneAutorizacao(telefoneAutorizacao);
        }

        return instituicaoRepository.save(instituicao);
    }

    /**
     * Solicita um OTP para o telefone de autorização da Instituição
     */
    @Transactional
    public void solicitarOtpAutorizacao() {
        Instituicao instituicao = getInstituicaoAtiva();
        
        if (instituicao.getTelefoneAutorizacao() == null || instituicao.getTelefoneAutorizacao().isBlank()) {
            throw new BusinessException("A instituição não possui telefone de autorização configurado");
        }

        String otp = gerarOtp();
        instituicao.setOtpCode(otp);
        instituicao.setOtpExpiration(java.time.LocalDateTime.now().plusMinutes(otpExpirationMinutes));
        
        instituicaoRepository.save(instituicao);

        if (otpMock) {
            log.info("🔧 MOCK OTP ATIVADO: O código OTP de AUTORIZAÇÃO para {} é [{}] (válido por {} min)", 
                instituicao.getTelefoneAutorizacao(), otp, otpExpirationMinutes);
        } else {
            boolean enviado = notificacaoService.enviarOtp(instituicao.getTelefoneAutorizacao(), otp);
            if (enviado) {
                log.info("OTP de autorização enviado via SMS para {}", instituicao.getTelefoneAutorizacao());
            } else {
                log.warn("Falha ao enviar OTP de autorização via SMS para {}", instituicao.getTelefoneAutorizacao());
            }
        }
    }

    /**
     * Valida o OTP recebido
     */
    @Transactional
    public void validarOtpAutorizacao(String codigo) {
        Instituicao instituicao = getInstituicaoAtiva();
        
        if (!instituicao.isOtpValido(codigo)) {
            throw new BusinessException("Código OTP de autorização inválido ou expirado");
        }

        // Limpa o OTP usado
        instituicao.setOtpCode(null);
        instituicao.setOtpExpiration(null);
        instituicaoRepository.save(instituicao);
        
        log.info("✅ Código OTP de autorização validado com sucesso.");
    }

    private String gerarOtp() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
