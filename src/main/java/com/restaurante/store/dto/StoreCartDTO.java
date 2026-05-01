package com.restaurante.store.dto;

import java.util.ArrayList;
import java.util.List;

public class StoreCartDTO {
    private List<StoreCarrinhoItemRequest> itens = new ArrayList<>();

    public List<StoreCarrinhoItemRequest> getItens() { return itens; }
    public void setItens(List<StoreCarrinhoItemRequest> itens) { this.itens = itens; }
}
