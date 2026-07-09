package com.restaurante.dto.response;

import com.restaurante.consumo.participante.service.SessaoParticipanteExtendedListService;

import java.util.List;

/**
 * Prompt 41.4 — Response de listagem paginada de participantes.
 */
public class SessaoParticipantePageResponse {

    private List<SessaoParticipanteExtendedListService.ParticipanteItemView> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean extended;

    public SessaoParticipantePageResponse() {}

    public SessaoParticipantePageResponse(SessaoParticipanteExtendedListService.ParticipantePage p) {
        this.items = p.items();
        this.page = p.page();
        this.size = p.size();
        this.totalElements = p.totalElements();
        this.totalPages = p.totalPages();
        this.extended = p.extended();
    }

    public List<SessaoParticipanteExtendedListService.ParticipanteItemView> getItems() { return items; }
    public void setItems(List<SessaoParticipanteExtendedListService.ParticipanteItemView> items) { this.items = items; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public boolean isExtended() { return extended; }
    public void setExtended(boolean extended) { this.extended = extended; }
}
