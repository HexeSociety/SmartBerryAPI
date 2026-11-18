package br.com.smartberry.service;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.repository.RepositorioDadosSensor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoMonitoramentoTest {

    @Mock
    private RepositorioDadosSensor repositorioDadosSensor;

    @Mock
    private ServicoEsp32 servicoEsp32;

    @InjectMocks
    private ServicoMonitoramento servicoMonitoramento;

    @Test
    void deveCalcularEstatisticasDasLeituras() {

        String esp32Id = "esp32-teste";

        DispositivoEsp32 dispositivo = new DispositivoEsp32();
        dispositivo.setId(esp32Id);
        dispositivo.setNome("ESP32 Principal");

        LocalDateTime agora = LocalDateTime.now();

        DadosSensor leitura1 = new DadosSensor();
        leitura1.setEsp32Id(esp32Id);
        leitura1.setTemperatura(24.0);
        leitura1.setUmidade(70.0);
        leitura1.setDataRegistro(agora.minusMinutes(2));

        DadosSensor leitura2 = new DadosSensor();
        leitura2.setEsp32Id(esp32Id);
        leitura2.setTemperatura(26.0);
        leitura2.setUmidade(68.0);
        leitura2.setDataRegistro(agora);

        when(servicoEsp32.buscarPorId(esp32Id))
                .thenReturn(dispositivo);

        when(repositorioDadosSensor
                .findFirstByEsp32IdOrderByDataRegistroDesc(esp32Id))
                .thenReturn(Optional.of(leitura2));

        when(repositorioDadosSensor
                .findByEsp32IdAndDataRegistroBetweenOrderByDataRegistroAsc(
                        eq(esp32Id),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class)
                ))
                .thenReturn(List.of(leitura1, leitura2));

        var resumo = servicoMonitoramento.consultarAtual(esp32Id);

        assertEquals(26.0, resumo.temperaturaAtual());
        assertEquals(24.0, resumo.temperaturaMinima24h());
        assertEquals(26.0, resumo.temperaturaMaxima24h());
        assertEquals(25.0, resumo.temperaturaMedia24h());

        assertEquals(69.0, resumo.umidadeMedia24h());
        assertEquals(2, resumo.totalLeituras24h());
    }
}
