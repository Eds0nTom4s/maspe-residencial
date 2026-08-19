package com.restaurante.controller;

import com.restaurante.fiscal.service.FiscalDocumentDeliveryService;
import com.restaurante.fiscal.service.FiscalDocumentPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/fiscal/documents")
@RequiredArgsConstructor
public class PublicFiscalDocumentController {
    private final FiscalDocumentDeliveryService deliveryService;
    private final FiscalDocumentPdfService pdfService;

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        var file = pdfService.render(deliveryService.resolvePublicToken(token));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.filename() + "\"")
                .body(file.bytes());
    }
}
