package br.com.smartberry.controlador;

import br.com.smartberry.modelo.DadosSensor;
import br.com.smartberry.servico.ServicoDadosSensor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(br.com.smartberry.controlador.ControladorDadosSensor.class)
class ControladorDadosSensorTest{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServicoDadosSensor servicoDadosSensor;

    @Test
    @DisplayName("Deve receber POST do ESP32 e retornar 201 Created")
    void deveReceberDadosDoEsp32ComSucesso() throws Exception {
        DadosSensor dadosRecebidos = new DadosSensor();
        DadosRecebidos.setTemperatura(26.0);
        DadosRecebidos.setUmidade(55.5);

        DadosSensor dadosSalvos = new DadosSensor();
        dadosSalvos.setId("12345abcde");
        dadosSalvos.setTemperatura(26.0);
        dadosSalvos.setUmidade(55.5);

        when(servicoDadosSensor.salvarDados(any(DadosSensor.class))).thenReturn(dadosSalvos);

        mockMvc.perform(post("/api/sensores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dadosRecebidos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("12345abcde"))
                .andExpect(jsonPath("$.temperatura").value(26.0))
                .andExpect(jsonPath("$.umidade").value(55.5));
    }
}