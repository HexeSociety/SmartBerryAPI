package br.com.smartberry.controller;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.service.ServicoComandoEsp32;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comandos")
@RequiredArgsConstructor
public class ControladorComandoEsp32 {
    private final ServicoComandoEsp32 servicoComandoEsp32;

    @PostMapping
    public ResponseEntity<ComandoEsp32> criarComando(@RequestBody ComandoEsp32 comando) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicoComandoEsp32.criarComando(comando));
    }

    @GetMapping("/pendentes")
    public ResponseEntity<List<ComandoEsp32>> listarPendentes(Authentication autenticacao) {
        return ResponseEntity.ok(servicoComandoEsp32.listarPendentes(autenticacao.getName()));
    }

    @PatchMapping("/{id}/executado")
    public ResponseEntity<ComandoEsp32> marcarComoExecutado(@PathVariable String id, Authentication autenticacao) {
        return ResponseEntity.ok(servicoComandoEsp32.marcarComoExecutado(id, autenticacao.getName()));
    }

    @PatchMapping("/{id}/falhou")
    public ResponseEntity<ComandoEsp32> marcarComoFalhou(@PathVariable String id, Authentication autenticacao) {
        return ResponseEntity.ok(servicoComandoEsp32.marcarComoFalhou(id, autenticacao.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> tratarArgumentoInvalido(IllegalArgumentException excecao) {
        return ResponseEntity.badRequest().body(excecao.getMessage());
    }
}
