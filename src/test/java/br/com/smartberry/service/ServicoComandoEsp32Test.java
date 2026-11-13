package br.com.smartberry.service;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.repository.RepositorioComandoEsp32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ServicoComandoEsp32Test {

    @Mock
    private RepositorioComandoEsp32 repositorioComandoEsp32;

    @InjectMocks
    private ServicoComandoEsp32 servicoComandoEsp32;

    private ComandoEsp32 criarComandoDe(TipoComando tipo, Integer valor) {
        ComandoEsp32 comando = new ComandoEsp32();
        comando.setTipo(tipo);
        comando.setValor(valor);
        return comando;
    }

    @Test
    @DisplayName("Deve criar comando de irrigação com status PENDENTE e data de criação")
    void deveCriarComandoDeIrrigacaoComStatusPendente() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 30);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.criarComando(comando);

        assertEquals(StatusComando.PENDENTE, resultado.getStatus());
        assertNotNull(resultado.getDataCriacao());
        verify(repositorioComandoEsp32).save(comando);
    }

    @Test
    @DisplayName("Deve aceitar duração exatamente igual ao máximo permitido")
    void deveAceitarDuracaoNoLimiteMaximo() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 300);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        assertDoesNotThrow(() -> servicoComandoEsp32.criarComando(comando));
    }

    @Test
    @DisplayName("Deve rejeitar irrigação sem duração")
    void deveRejeitarIrrigacaoSemDuracao() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, null);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar irrigação com duração menor ou igual a zero")
    void deveRejeitarIrrigacaoComDuracaoInvalida() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 0);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar irrigação com duração acima do máximo")
    void deveRejeitarIrrigacaoAcimaDoMaximo() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 301);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve criar comando de parar irrigação sem exigir valor")
    void deveCriarPararIrrigacaoSemValor() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.PARAR_IRRIGACAO, null);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.criarComando(comando);

        assertEquals(StatusComando.PENDENTE, resultado.getStatus());
    }

    @Test
    @DisplayName("Deve rejeitar alterar intervalo sem valor")
    void deveRejeitarAlterarIntervaloSemValor() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ALTERAR_INTERVALO, null);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar intervalo menor ou igual a zero")
    void deveRejeitarIntervaloInvalido() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ALTERAR_INTERVALO, 0);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
    }

    @Test
    @DisplayName("Deve rejeitar comando sem tipo")
    void deveRejeitarComandoSemTipo() {
        ComandoEsp32 comando = criarComandoDe(null, 10);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
    }

    @Test
    @DisplayName("Deve listar apenas comandos pendentes")
    void deveListarComandosPendentes() {
        ComandoEsp32 comando = new ComandoEsp32();
        comando.setStatus(StatusComando.PENDENTE);

        when(repositorioComandoEsp32.findByStatus(StatusComando.PENDENTE))
                .thenReturn(List.of(comando));

        List<ComandoEsp32> resultado = servicoComandoEsp32.listarPendentes();

        assertEquals(1, resultado.size());
        assertEquals(StatusComando.PENDENTE, resultado.get(0).getStatus());
    }

    @Test
    @DisplayName("Deve marcar comando pendente como executado e salvar")
    void deveMarcarComandoPendenteComoExecutado() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 30);
        comando.setId("cmd123");
        comando.setStatus(StatusComando.PENDENTE);

        when(repositorioComandoEsp32.findById("cmd123")).thenReturn(Optional.of(comando));
        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.marcarComoExecutado("cmd123");

        assertEquals(StatusComando.EXECUTADO, resultado.getStatus());
        verify(repositorioComandoEsp32).save(comando);
    }

    @Test
    @DisplayName("Deve manter os demais campos ao marcar como executado")
    void deveManterDemaisCamposAoMarcarComoExecutado() {
        LocalDateTime dataCriacao = LocalDateTime.of(2026, 11, 5, 10, 0);
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 30);
        comando.setId("cmd123");
        comando.setStatus(StatusComando.PENDENTE);
        comando.setDataCriacao(dataCriacao);

        when(repositorioComandoEsp32.findById("cmd123")).thenReturn(Optional.of(comando));
        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.marcarComoExecutado("cmd123");

        assertEquals("cmd123", resultado.getId());
        assertEquals(TipoComando.ACIONAR_IRRIGACAO, resultado.getTipo());
        assertEquals(30, resultado.getValor());
        assertEquals(dataCriacao, resultado.getDataCriacao());
    }

    @Test
    @DisplayName("Deve lançar 404 quando o comando não existe")
    void deveLancarNaoEncontradoQuandoComandoNaoExiste() {
        when(repositorioComandoEsp32.findById("inexistente")).thenReturn(Optional.empty());

        ResponseStatusException excecao = assertThrows(ResponseStatusException.class,
                () -> servicoComandoEsp32.marcarComoExecutado("inexistente"));

        assertEquals(HttpStatus.NOT_FOUND.value(), excecao.getStatusCode().value());
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve manter o status EXECUTADO quando o comando já foi executado")
    void deveManterExecutadoQuandoComandoJaFoiExecutado() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.PARAR_IRRIGACAO, null);
        comando.setId("cmd123");
        comando.setStatus(StatusComando.EXECUTADO);

        when(repositorioComandoEsp32.findById("cmd123")).thenReturn(Optional.of(comando));
        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.marcarComoExecutado("cmd123");

        assertEquals(StatusComando.EXECUTADO, resultado.getStatus());
    }

    @Test
    @DisplayName("Deve criar comando de alterar intervalo com valor válido como PENDENTE")
    void deveCriarAlterarIntervaloComValorValido() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ALTERAR_INTERVALO, 60);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.criarComando(comando);

        assertEquals(StatusComando.PENDENTE, resultado.getStatus());
        assertEquals(60, resultado.getValor());
    }

    @Test
    @DisplayName("Deve rejeitar intervalo negativo")
    void deveRejeitarIntervaloNegativo() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ALTERAR_INTERVALO, -5);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar irrigação com duração negativa")
    void deveRejeitarIrrigacaoComDuracaoNegativa() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, -1);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve aceitar irrigação com duração de 1 segundo (limite inferior)")
    void deveAceitarIrrigacaoNoLimiteInferior() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 1);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        assertDoesNotThrow(() -> servicoComandoEsp32.criarComando(comando));
    }

    @Test
    @DisplayName("Deve forçar status PENDENTE mesmo quando o cliente envia EXECUTADO")
    void deveForcarStatusPendenteQuandoClienteEnviaExecutado() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 30);
        comando.setStatus(StatusComando.EXECUTADO);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.criarComando(comando);

        assertEquals(StatusComando.PENDENTE, resultado.getStatus());
    }

    @Test
    @DisplayName("Deve sobrescrever a data de criação enviada pelo cliente")
    void deveSobrescreverDataCriacaoEnviadaPeloCliente() {
        LocalDateTime dataAntiga = LocalDateTime.of(2000, 1, 1, 0, 0);
        ComandoEsp32 comando = criarComandoDe(TipoComando.ACIONAR_IRRIGACAO, 30);
        comando.setDataCriacao(dataAntiga);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        ComandoEsp32 resultado = servicoComandoEsp32.criarComando(comando);

        assertTrue(resultado.getDataCriacao().isAfter(dataAntiga));
    }

    @Test
    @DisplayName("Deve aceitar parar irrigação mesmo com valor preenchido")
    void deveAceitarPararIrrigacaoComValorPreenchido() {
        ComandoEsp32 comando = criarComandoDe(TipoComando.PARAR_IRRIGACAO, 99);

        when(repositorioComandoEsp32.save(any(ComandoEsp32.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        assertDoesNotThrow(() -> servicoComandoEsp32.criarComando(comando));
    }

    @Test
    @DisplayName("Não deve salvar quando o comando não tem tipo")
    void naoDeveSalvarQuandoComandoNaoTemTipo() {
        ComandoEsp32 comando = criarComandoDe(null, 10);

        assertThrows(IllegalArgumentException.class,
                () -> servicoComandoEsp32.criarComando(comando));
        verify(repositorioComandoEsp32, never()).save(any());
    }

    @Test
    @DisplayName("Deve devolver lista vazia, e não nula, quando não há pendentes")
    void deveDevolverListaVaziaQuandoNaoHaPendentes() {
        when(repositorioComandoEsp32.findByStatus(StatusComando.PENDENTE))
                .thenReturn(List.of());

        List<ComandoEsp32> resultado = servicoComandoEsp32.listarPendentes();

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }
}