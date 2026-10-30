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
        dadosRecebidos.setTemperatura(26.0); // Corrigido de DadosRecebidos para dadosRecebidos
        dadosRecebidos.setUmidade(55.5);     // Corrigido de DadosRecebidos para dadosRecebidos

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
}