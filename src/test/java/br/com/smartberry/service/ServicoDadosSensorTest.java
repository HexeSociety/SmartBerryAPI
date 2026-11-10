package br.com.smartberry.service;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.repository.RepositorioDadosSensor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoDadosSensorTest {

    @Mock
    private RepositorioDadosSensor repositorioDadosSensor;

    @InjectMocks
    private ServicoDadosSensor servicoDadosSensor;

    private DadosSensor criarLeitura(Double temperatura, Double umidade) {
        DadosSensor dados = new DadosSensor();
        dados.setTemperatura(temperatura);
        dados.setUmidade(umidade);
        return dados;
    }

    @Test
    @DisplayName("Deve salvar a leitura e devolver o que o repositório retornou")
    void deveSalvarLeituraEDevolverRetornoDoRepositorio() {
        DadosSensor recebida = criarLeitura(26.0, 55.5);
        DadosSensor persistida = criarLeitura(26.0, 55.5);
        persistida.setId("12345abcde");

        when(repositorioDadosSensor.save(any(DadosSensor.class))).thenReturn(persistida);

        DadosSensor resultado = servicoDadosSensor.salvarDados(recebida);

        assertSame(persistida, resultado);
        assertEquals("12345abcde", resultado.getId());
    }

    @Test
    @DisplayName("Deve definir a data de registro com a hora atual")
    void deveDefinirDataRegistroComHoraAtual() {
        DadosSensor recebida = criarLeitura(26.0, 55.5);
        when(repositorioDadosSensor.save(any(DadosSensor.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        LocalDateTime antes = LocalDateTime.now();
        servicoDadosSensor.salvarDados(recebida);
        LocalDateTime depois = LocalDateTime.now();

        ArgumentCaptor<DadosSensor> captor = ArgumentCaptor.forClass(DadosSensor.class);
        verify(repositorioDadosSensor).save(captor.capture());

        LocalDateTime dataRegistro = captor.getValue().getDataRegistro();
        assertNotNull(dataRegistro);
        assertFalse(dataRegistro.isBefore(antes));
        assertFalse(dataRegistro.isAfter(depois));
    }

    @Test
    @DisplayName("Deve sobrescrever a data de registro enviada pelo cliente")
    void deveSobrescreverDataRegistroEnviadaPeloCliente() {
        DadosSensor recebida = criarLeitura(26.0, 55.5);
        LocalDateTime dataAntiga = LocalDateTime.of(2000, 1, 1, 0, 0);
        recebida.setDataRegistro(dataAntiga);

        when(repositorioDadosSensor.save(any(DadosSensor.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        DadosSensor resultado = servicoDadosSensor.salvarDados(recebida);

        assertNotEquals(dataAntiga, resultado.getDataRegistro());
        assertTrue(resultado.getDataRegistro().isAfter(dataAntiga));
    }

    @Test
    @DisplayName("Deve manter temperatura e umidade recebidas")
    void deveManterTemperaturaEUmidadeRecebidas() {
        DadosSensor recebida = criarLeitura(26.0, 55.5);
        when(repositorioDadosSensor.save(any(DadosSensor.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        DadosSensor resultado = servicoDadosSensor.salvarDados(recebida);

        assertEquals(26.0, resultado.getTemperatura());
        assertEquals(55.5, resultado.getUmidade());
    }

    @Test
    @DisplayName("Deve chamar o repositório uma única vez")
    void deveChamarRepositorioUmaUnicaVez() {
        DadosSensor recebida = criarLeitura(26.0, 55.5);
        when(repositorioDadosSensor.save(any(DadosSensor.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));

        servicoDadosSensor.salvarDados(recebida);

        verify(repositorioDadosSensor, times(1)).save(recebida);
        verifyNoMoreInteractions(repositorioDadosSensor);
    }

    @Test
    @DisplayName("Deve propagar a exceção quando o repositório falhar")
    void devePropagarExcecaoQuandoRepositorioFalhar() {
        DadosSensor recebida = criarLeitura(26.0, 55.5);
        when(repositorioDadosSensor.save(any(DadosSensor.class)))
                .thenThrow(new RuntimeException("Falha no banco"));

        assertThrows(RuntimeException.class,
                () -> servicoDadosSensor.salvarDados(recebida));
    }
}