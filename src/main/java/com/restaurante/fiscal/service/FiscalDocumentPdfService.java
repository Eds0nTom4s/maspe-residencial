package com.restaurante.fiscal.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.repository.FiscalDocumentLineRepository;
import com.restaurante.fiscal.repository.TenantFiscalProfileRepository;
import com.restaurante.model.entity.FiscalDocument;
import com.restaurante.model.entity.FiscalDocumentLine;
import com.restaurante.model.entity.TenantFiscalProfile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FiscalDocumentPdfService {

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FiscalDocumentLineRepository lineRepository;
    private final TenantFiscalProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public PdfFile render(FiscalDocument document) {
        if (document == null || document.getTenant() == null) {
            throw new BusinessException("Documento interno não encontrado.");
        }
        TenantFiscalProfile profile = profileRepository.findByTenantId(document.getTenant().getId()).orElse(null);
        List<FiscalDocumentLine> lines = lineRepository.findByFiscalDocumentIdOrderByIdAsc(document.getId());
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (PDPageContentStream canvas = new PDPageContentStream(pdf, page)) {
                float y = 800;
                y = text(canvas, BOLD, 16, 50, y, "DOCUMENTO INTERNO DA PLATAFORMA");
                y = text(canvas, BOLD, 11, 50, y, documentTitle(document));
                y = text(canvas, REGULAR, 8, 50, y,
                        "NÃO REPRESENTA CERTIFICAÇÃO OU SUBMISSÃO ELECTRÓNICA OFICIAL À AGT");
                if (document.getStatus() != null && document.getStatus().name().equals("CANCELLED")) {
                    y = text(canvas, BOLD, 12, 50, y, "DOCUMENTO CANCELADO");
                }
                y -= 10;
                y = text(canvas, BOLD, 12, 50, y,
                        value(profile != null ? profile.getLegalName() : null, document.getTenant().getNome()));
                y = text(canvas, REGULAR, 10, 50, y, "NIF: "
                        + value(profile != null ? profile.getTaxpayerNumber() : null, document.getTenant().getNif()));
                y = text(canvas, REGULAR, 10, 50, y, joinAddress(profile));
                y -= 8;
                y = text(canvas, REGULAR, 10, 50, y, "Documento: "
                        + value(document.getDocumentNumber(), "-") + "  Série: " + value(document.getSeries(), "-"));
                y = text(canvas, REGULAR, 10, 50, y, "Data: "
                        + (document.getIssuedAt() != null ? DATE_TIME.format(document.getIssuedAt()) : "-"));
                y = text(canvas, REGULAR, 10, 50, y,
                        "Cliente: " + value(document.getCustomerName(), "Consumidor final"));
                y -= 12;
                y = text(canvas, BOLD, 9, 50, y, "Descrição");
                text(canvas, BOLD, 9, 330, y + 14, "Qtd.");
                text(canvas, BOLD, 9, 380, y + 14, "Preço");
                text(canvas, BOLD, 9, 470, y + 14, "Total");
                for (FiscalDocumentLine line : lines) {
                    if (y < 90) break;
                    y = text(canvas, REGULAR, 9, 50, y, truncate(line.getDescription(), 48));
                    text(canvas, REGULAR, 9, 330, y + 14, String.valueOf(line.getQuantity()));
                    text(canvas, REGULAR, 9, 380, y + 14, money(line.getUnitPrice()));
                    text(canvas, REGULAR, 9, 470, y + 14, money(line.getGrossAmount()));
                }
                y -= 10;
                y = text(canvas, REGULAR, 10, 360, y, "Subtotal: " + money(document.getSubtotalAmount()));
                y = text(canvas, REGULAR, 10, 360, y, "IVA: " + money(document.getTaxAmount()));
                text(canvas, BOLD, 12, 360, y, "TOTAL: " + money(document.getTotalAmount()));
                text(canvas, REGULAR, 8, 50, 45,
                        "Comprovativo interno gerado pelo CONSUMA - valores em AOA");
            }
            pdf.save(output);
            return new PdfFile(output.toByteArray(),
                    "documento-interno-" + safeFilename(document.getDocumentNumber()) + ".pdf");
        } catch (IOException ex) {
            throw new BusinessException("Não foi possível gerar o PDF do documento interno.");
        }
    }

    private static float text(PDPageContentStream canvas, PDType1Font font, float size,
                              float x, float y, String value) throws IOException {
        canvas.beginText();
        canvas.setFont(font, size);
        canvas.newLineAtOffset(x, y);
        canvas.showText(printable(value));
        canvas.endText();
        return y - 14;
    }

    private static String printable(String value) {
        return value(value, "-").replace('\n', ' ').replace('\r', ' ')
                .replace("→", "-").replace("—", "-");
    }

    private static String documentTitle(FiscalDocument document) {
        return switch (document.getDocumentType()) {
            case INTERNAL_INVOICE -> "FACTURA INTERNA";
            case INTERNAL_INVOICE_RECEIPT -> "FACTURA-RECIBO INTERNA";
            case INTERNAL_RECEIPT -> "RECIBO INTERNO";
            case INTERNAL_CREDIT_NOTE, INTERNAL_CREDIT_NOTE_PLACEHOLDER -> "NOTA DE CRÉDITO INTERNA";
            case INTERNAL_DEBIT_NOTE -> "NOTA DE DÉBITO INTERNA";
        };
    }

    private static String joinAddress(TenantFiscalProfile profile) {
        if (profile == null) return "";
        return java.util.stream.Stream.of(profile.getAddress(), profile.getMunicipality(),
                        profile.getProvince(), profile.getCountryCode())
                .filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(", "));
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-AO"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount != null ? amount : BigDecimal.ZERO);
    }

    private static String truncate(String value, int max) {
        String normalized = value(value, "Item");
        return normalized.length() <= max ? normalized : normalized.substring(0, max - 1) + "...";
    }

    private static String value(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred.trim() : fallback;
    }

    private static String safeFilename(String value) {
        return value(value, "documento").replaceAll("[^A-Za-z0-9._-]", "-");
    }

    public record PdfFile(byte[] bytes, String filename) { }
}
