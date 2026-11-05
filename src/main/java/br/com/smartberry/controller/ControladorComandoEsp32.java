package br.com.smartberry.controller;

import br.com.smartberry.model.ComandoEsp32;
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