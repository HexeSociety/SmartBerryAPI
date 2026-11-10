package br.com.smartberry.controller;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.service.ServicoComandoEsp32;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@WebMvcTest(ControladorComandoEsp32.class)
class ControladorComandoEsp32Test {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ServicoComandoEsp32 servicoComandoEsp32;

    @Test
    @DisplayName("Deve criar comando de irrigação e retornar 201 Created")
    void deveCriarComandoComSucesso() throws Exception {
        ComandoEsp32 requisicao = new ComandoEsp32();
        requisicao.setTipo(TipoComando.ACIONAR_IRRIGACAO);
        requisicao.setValor(30);

        ComandoEsp32 salvo = new ComandoEsp32();
        salvo.setId("cmd123");
        salvo.setTipo(TipoComando.ACIONAR_IRRIGACAO);
        salvo.setValor(30);
        salvo.setStatus(StatusComando.PENDENTE);

        when(servicoComandoEsp32.criarComando(any(ComandoEsp32.class))).thenReturn(salvo);

        mockMvc.perform(post("/api/comandos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cmd123"))
                .andExpect(jsonPath("$.tipo").value("ACIONAR_IRRIGACAO"))
                .andExpect(jsonPath("$.valor").value(30))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    @DisplayName("Deve retornar 400 quando o comando for inválido")
    void deveRetornarBadRequestParaComandoInvalido() throws Exception {
        ComandoEsp32 requisicao = new ComandoEsp32();
        requisicao.setTipo(TipoComando.ACIONAR_IRRIGACAO);

        when(servicoComandoEsp32.criarComando(any(ComandoEsp32.class)))
                .thenThrow(new IllegalArgumentException("Duração obrigatória"));

        mockMvc.perform(post("/api/comandos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requisicao)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve listar comandos pendentes para o ESP32")
    void deveListarComandosPendentes() throws Exception {
        ComandoEsp32 comando = new ComandoEsp32();
        comando.setId("cmd123");
        comando.setTipo(TipoComando.ACIONAR_IRRIGACAO);
        comando.setValor(30);
        comando.setStatus(StatusComando.PENDENTE);

        when(servicoComandoEsp32.listarPendentes()).thenReturn(List.of(comando));

        mockMvc.perform(get("/api/comandos/pendentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cmd123"))
                .andExpect(jsonPath("$[0].tipo").value("ACIONAR_IRRIGACAO"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }

    @Test
    @DisplayName("Deve marcar comando como executado e retornar 200")
    void deveMarcarComandoComoExecutado() throws Exception {
        ComandoEsp32 executado = new ComandoEsp32();
        executado.setId("cmd123");
        executado.setTipo(TipoComando.ACIONAR_IRRIGACAO);
        executado.setValor(30);
        executado.setStatus(StatusComando.EXECUTADO);

        when(servicoComandoEsp32.marcarComoExecutado("cmd123")).thenReturn(executado);

        mockMvc.perform(patch("/api/comandos/cmd123/executado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cmd123"))
                .andExpect(jsonPath("$.status").value("EXECUTADO"));
    }

    @Test
    @DisplayName("Deve retornar 404 quando o comando não existe")
    void deveRetornarNaoEncontradoQuandoComandoNaoExiste() throws Exception {
        when(servicoComandoEsp32.marcarComoExecutado("inexistente"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comando não encontrado"));

        mockMvc.perform(patch("/api/comandos/inexistente/executado"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve rejeitar POST no endpoint de execução com 405")
    void deveRejeitarMetodoIncorretoNoEndpointDeExecucao() throws Exception {
        mockMvc.perform(post("/api/comandos/cmd123/executado"))
                .andExpect(status().isMethodNotAllowed());
    }
}