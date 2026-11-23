package br.com.smartberry.controller;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.service.ServicoComandoEsp32;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comandos")
@RequiredArgsConstructor
public class ControladorComandoEsp32 {

    private final ServicoComandoEsp32 servicoComandoEsp32;

    @PostMapping
    public ResponseEntity<ComandoEsp32> criarComando(@RequestBody ComandoEsp32 comando) {
        ComandoEsp32 comandoSalvo = servicoComandoEsp32.criarComando(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(comandoSalvo);
    }

    @GetMapping("/pendentes")
    public ResponseEntity<List<ComandoEsp32>> listarPendentes() {
        return ResponseEntity.ok(servicoComandoEsp32.listarPendentes());
    }

    @GetMapping("/esp32/{esp32Id}/proximo")
    public ResponseEntity<ComandoEsp32> proximo(@PathVariable String esp32Id) {
        return servicoComandoEsp32.proximo(esp32Id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ComandoEsp32 buscar(@PathVariable String id) {
        return servicoComandoEsp32.buscar(id);
    }

    @GetMapping("/esp32/{esp32Id}/historico-irrigacao")
    public List<ComandoEsp32> historicoIrrigacao(
            @PathVariable String esp32Id, @RequestParam int dias) {
        return servicoComandoEsp32.historicoIrrigacao(esp32Id, dias);
    }

    @PatchMapping("/{id}/rejeitado")
    public ComandoEsp32 rejeitar(@PathVariable String id) {
        return servicoComandoEsp32.concluir(id, StatusComando.REJEITADO);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> tratarArgumentoInvalido(IllegalArgumentException excecao) {
        return ResponseEntity.badRequest().body(excecao.getMessage());
    }

    @PatchMapping("/{id}/executado")
    public ResponseEntity<ComandoEsp32> marcarComoExecutado(
            @PathVariable String id) {

        ComandoEsp32 comando = servicoComandoEsp32.marcarComoExecutado(id);

        return ResponseEntity.ok(comando);
    }
}