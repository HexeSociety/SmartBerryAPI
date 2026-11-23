package br.com.smartberry.controller;

import br.com.smartberry.model.AgendamentoIrrigacao.Equipamento;
import br.com.smartberry.service.ServicoAgendamentoIrrigacao;
import br.com.smartberry.service.ServicoAgendamentoIrrigacao.Resumo;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/esp32/{esp32Id}/{recurso:agendamentos-irrigacao|agendamentos-ventilacao}")
@RequiredArgsConstructor
public class ControladorAgendamentoIrrigacao {
    private final ServicoAgendamentoIrrigacao servico;

    public record Pedido(LocalDateTime horario, Integer duracaoSegundos, Boolean diario) {}
    public record Modo(Boolean ativo) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Resumo criar(@PathVariable String esp32Id, @PathVariable String recurso, @RequestBody Pedido pedido) {
        return servico.criar(esp32Id, equipamento(recurso), pedido.horario(), pedido.duracaoSegundos(), Boolean.TRUE.equals(pedido.diario()));
    }

    @GetMapping
    public List<Resumo> listar(@PathVariable String esp32Id, @PathVariable String recurso) {
        return servico.listar(esp32Id, equipamento(recurso));
    }

    @PutMapping("/{id}")
    public Resumo editar(@PathVariable String esp32Id, @PathVariable String recurso,
                         @PathVariable String id, @RequestBody Pedido pedido) {
        return servico.editar(esp32Id, equipamento(recurso), id, pedido.horario(), pedido.duracaoSegundos(), Boolean.TRUE.equals(pedido.diario()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable String esp32Id, @PathVariable String recurso, @PathVariable String id) {
        servico.cancelar(esp32Id, equipamento(recurso), id);
    }

    @GetMapping("/automatico")
    public Modo modo(@PathVariable String esp32Id, @PathVariable String recurso) {
        return new Modo(servico.modo(esp32Id, equipamento(recurso)));
    }

    @PutMapping("/automatico")
    public Modo modo(@PathVariable String esp32Id, @PathVariable String recurso, @RequestBody Modo modo) {
        if (modo.ativo() == null) throw new IllegalArgumentException("Informe o estado do modo diário");
        servico.alterarModo(esp32Id, equipamento(recurso), modo.ativo());
        return modo;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String argumentoInvalido(IllegalArgumentException erro) {
        return erro.getMessage();
    }

    private Equipamento equipamento(String recurso) {
        return "agendamentos-ventilacao".equals(recurso) ? Equipamento.VENTILACAO : Equipamento.IRRIGACAO;
    }
}
