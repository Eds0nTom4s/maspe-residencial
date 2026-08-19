package com.restaurante.fiscal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.repository.FiscalDocumentRepository;
import com.restaurante.fiscal.service.FiscalDocumentDeliveryService;
import com.restaurante.fiscal.service.FiscalDocumentService;
import com.restaurante.notificacao.service.NotificacaoService;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class FiscalDocumentDeliveryServiceTest {

    @Test
    void externalSmsDeliveryIsDisabledByDefaultWithoutContactingProvider() {
        FiscalDocumentRepository documents = mock(FiscalDocumentRepository.class);
        FiscalDocumentService fiscal = mock(FiscalDocumentService.class);
        NotificacaoService notifications = mock(NotificacaoService.class);
        FiscalDocumentDeliveryService service = new FiscalDocumentDeliveryService(
                documents, fiscal, notifications, Clock.systemUTC());

        assertThatThrownBy(() -> service.sendBySms(20L, "+244923000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não está certificada");
        verifyNoInteractions(documents, fiscal, notifications);
    }
}
