
package br.com.smartberry.controller;

import br.com.smartberry.dto.PontoGrafico;
import br.com.smartberry.dto.ResumoMonitoramento;
import br.com.smartberry.service.ServicoMonitoramento;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/monitoramento")
@RequiredArgsConstructor
public class ControladorMonitoramento {

    private final ServicoMonitoramento servicoMonitoramento;

    // Dados atuais para o dashboard
    @GetMapping("/{esp32Id}/atual")
    public ResumoMonitoramento consultarAtual(
            @PathVariable String esp32Id
    ) {
        return servicoMonitoramento.consultarAtual(esp32Id);
    }

    // Gráfico agrupado por hora
    @GetMapping("/{esp32Id}/grafico")
    public List<PontoGrafico> consultarGrafico(
            @PathVariable String esp32Id,
            @RequestParam(defaultValue = "24") int horas,
            @RequestParam(defaultValue = "false") boolean hoje
    ) {
        if (hoje) return servicoMonitoramento.consultarGraficoHoje(esp32Id);
        return servicoMonitoramento.consultarGrafico(
                esp32Id, horas
        );
    }

    // Histórico agrupado por dia
    @GetMapping("/{esp32Id}/historico")
    public List<PontoGrafico> consultarHistorico(
            @PathVariable String esp32Id,
            @RequestParam(defaultValue = "7") int dias
    ) {
        return servicoMonitoramento.consultarHistorico(
                esp32Id, dias
        );
    }
}
