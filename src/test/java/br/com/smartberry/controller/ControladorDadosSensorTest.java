package br.com.smartberry.controller;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.service.ServicoDadosSensor;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ControladorDadosSensor.class)
class ControladorDadosSensorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ServicoDadosSensor servicoDadosSensor;

    @Test
    @DisplayName("Deve receber POST do ESP32 e retornar 201 Created")
    void deveReceberDadosDoEsp32ComSucesso() throws Exception {
        // Arrange
        DadosSensor dadosRecebidos = new DadosSensor();
        dadosRecebidos.setTemperatura(26.0);
        dadosRecebidos.setUmidade(55.5);

        DadosSensor dadosSalvos = new DadosSensor();
        dadosSalvos.setId("12345abcde");
        dadosSalvos.setTemperatura(26.0);
        dadosSalvos.setUmidade(55.5);

        when(servicoDadosSensor.salvarDados(any(DadosSensor.class))).thenReturn(dadosSalvos);

        // Act & Assert
        mockMvc.perform(post("/api/sensores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dadosRecebidos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("12345abcde"))
                .andExpect(jsonPath("$.temperatura").value(26.0))
                .andExpect(jsonPath("$.umidade").value(55.5));
    }

    @Test
    @DisplayName("Deve retornar 400 quando o JSON for malformado")
    void deveRetornarBadRequestQuandoJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/sensores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperatura\": 26.0, \"umidade\":"))
                .andExpect(status().isBadRequest());

        verify(servicoDadosSensor, never()).salvarDados(any());
    }

    @Test
    @DisplayName("Deve retornar 400 quando o corpo estiver vazio")
    void deveRetornarBadRequestQuandoCorpoVazio() throws Exception {
        mockMvc.perform(post("/api/sensores")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(servicoDadosSensor, never()).salvarDados(any());
    }

    @Test
    @DisplayName("Deve retornar 415 quando o Content-Type não for JSON")
    void deveRetornarUnsupportedMediaTypeQuandoContentTypeNaoForJson() throws Exception {
        mockMvc.perform(post("/api/sensores")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("temperatura=26.0"))
                .andExpect(status().isUnsupportedMediaType());

        verify(servicoDadosSensor, never()).salvarDados(any());
    }

    @Test
    @DisplayName("Deve retornar 400 quando a temperatura não for numérica")
    void deveRetornarBadRequestQuandoTemperaturaNaoForNumerica() throws Exception {
        mockMvc.perform(post("/api/sensores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperatura\": \"abc\", \"umidade\": 55.5}"))
                .andExpect(status().isBadRequest());

        verify(servicoDadosSensor, never()).salvarDados(any());
    }
}