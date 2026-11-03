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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
}