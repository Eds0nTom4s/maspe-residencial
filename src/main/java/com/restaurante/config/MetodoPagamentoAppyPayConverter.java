package com.restaurante.config;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MetodoPagamentoAppyPayConverter implements Converter<String, MetodoPagamentoAppyPay> {

    @Override
    public MetodoPagamentoAppyPay convert(String source) {
        return MetodoPagamentoAppyPay.from(source);
    }
}
