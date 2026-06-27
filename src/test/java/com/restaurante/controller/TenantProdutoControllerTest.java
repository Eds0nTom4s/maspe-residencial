package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ReordenarImagensRequest;
import com.restaurante.dto.response.ProdutoImagemResponse;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.security.JwtAuthenticationFilter;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.ProdutoImagemService;
import com.restaurante.service.ProdutoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TenantProdutoController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, JwtTokenProvider.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class TenantProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantGuard tenantGuard;

    @MockBean
    private ProdutoService produtoService;

    @MockBean
    private ProdutoImagemService produtoImagemService;

    @Test
    @DisplayName("POST /tenant/produtos/{id}/imagem - deve fazer upload da imagem principal")
    void deveFazerUploadImagemPrincipal() throws Exception {
        Long produtoId = 1L;
        ProdutoResponse response = ProdutoResponse.builder()
                .id(produtoId)
                .nome("Hambúrguer")
                .urlImagem("http://localhost:9000/restaurante-images/produtos/uuid.jpg")
                .build();

        when(produtoService.atualizarImagem(eq(produtoId), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "conteudo".getBytes());

        mockMvc.perform(multipart("/tenant/produtos/{id}/imagem", produtoId)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.urlImagem").value("http://localhost:9000/restaurante-images/produtos/uuid.jpg"));

        verify(produtoService, times(1)).atualizarImagem(eq(produtoId), any());
    }

    @Test
    @DisplayName("GET /tenant/produtos/{id}/imagens - deve listar imagens da galeria")
    void deveListarImagensDaGaleria() throws Exception {
        Long produtoId = 1L;
        ProdutoImagemResponse img = new ProdutoImagemResponse();
        img.setId(10L);
        img.setProdutoId(produtoId);
        img.setUrl("http://localhost:9000/restaurante-images/produtos/uuid.jpg");
        img.setOrdem(0);

        when(produtoImagemService.listarImagens(produtoId)).thenReturn(List.of(img));

        mockMvc.perform(get("/tenant/produtos/{id}/imagens", produtoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(10L))
                .andExpect(jsonPath("$.data[0].url").value("http://localhost:9000/restaurante-images/produtos/uuid.jpg"));

        verify(produtoImagemService, times(1)).listarImagens(produtoId);
    }

    @Test
    @DisplayName("POST /tenant/produtos/{id}/imagens - deve adicionar imagem à galeria")
    void deveAdicionarImagemAGaleria() throws Exception {
        Long produtoId = 1L;
        ProdutoImagemResponse response = new ProdutoImagemResponse();
        response.setId(10L);
        response.setProdutoId(produtoId);
        response.setUrl("http://localhost:9000/restaurante-images/produtos/uuid.jpg");
        response.setOrdem(0);
        response.setLegenda("Frente");

        when(produtoImagemService.adicionarImagem(eq(produtoId), any(), eq("Frente"))).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "conteudo".getBytes());

        mockMvc.perform(multipart("/tenant/produtos/{id}/imagens", produtoId)
                        .file(file)
                        .param("legenda", "Frente"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.legenda").value("Frente"));

        verify(produtoImagemService, times(1)).adicionarImagem(eq(produtoId), any(), eq("Frente"));
    }

    @Test
    @DisplayName("DELETE /tenant/produtos/{id}/imagens/{imagemId} - deve remover imagem")
    void deveRemoverImagem() throws Exception {
        Long produtoId = 1L;
        Long imagemId = 10L;

        doNothing().when(produtoImagemService).removerImagem(produtoId, imagemId);

        mockMvc.perform(delete("/tenant/produtos/{id}/imagens/{imagemId}", produtoId, imagemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(produtoImagemService, times(1)).removerImagem(produtoId, imagemId);
    }

    @Test
    @DisplayName("PUT /tenant/produtos/{id}/imagens/ordem - deve reordenar imagens")
    void deveReordenarImagens() throws Exception {
        Long produtoId = 1L;
        ReordenarImagensRequest request = new ReordenarImagensRequest();
        request.setImagemIds(List.of(2L, 1L));

        ProdutoImagemResponse img1 = new ProdutoImagemResponse();
        img1.setId(1L);
        img1.setOrdem(1);
        ProdutoImagemResponse img2 = new ProdutoImagemResponse();
        img2.setId(2L);
        img2.setOrdem(0);

        when(produtoImagemService.reordenarImagens(produtoId, List.of(2L, 1L))).thenReturn(List.of(img2, img1));

        mockMvc.perform(put("/tenant/produtos/{id}/imagens/ordem", produtoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2L))
                .andExpect(jsonPath("$.data[0].ordem").value(0));

        verify(produtoImagemService, times(1)).reordenarImagens(produtoId, List.of(2L, 1L));
    }
}
