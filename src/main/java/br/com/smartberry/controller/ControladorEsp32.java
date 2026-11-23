package br.com.smartberry.controller;

import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.service.ServicoEsp32;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/esp32")
@RequiredArgsConstructor
public class ControladorEsp32 {

    private final ServicoEsp32 servicoEsp32;

    // POST - Cadastrar ESP32
    @PostMapping
    public ResponseEntity<DispositivoEsp32> cadastrar(
            @RequestBody DispositivoEsp32 dados
    ) {
        DispositivoEsp32 dispositivo = servicoEsp32.cadastrar(dados);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dispositivo);
    }

    @PutMapping("/registro/{codigo}")
    public DispositivoEsp32 registrarAutomaticamente(@PathVariable String codigo) {
        return servicoEsp32.registrarAutomaticamente(codigo);
    }

    @GetMapping("/automatico")
    public DispositivoEsp32 selecionarAutomaticamente() {
        return servicoEsp32.selecionarAutomaticamente();
    }

    // GET - Listar dispositivos
    @GetMapping
    public ResponseEntity<List<DispositivoEsp32>> listar() {
        return ResponseEntity.ok(servicoEsp32.listar());
    }

    // GET - Buscar por ID
    @GetMapping("/{id}")
    public ResponseEntity<DispositivoEsp32> buscarPorId(
            @PathVariable String id
    ) {
        return ResponseEntity.ok(servicoEsp32.buscarPorId(id));
    }

    // PUT - Atualizar dispositivo
    @PutMapping("/{id}")
    public ResponseEntity<DispositivoEsp32> atualizar(
            @PathVariable String id,
            @RequestBody DispositivoEsp32 dados
    ) {
        return ResponseEntity.ok(servicoEsp32.atualizar(id, dados));
    }

    // DELETE - Exclusão lógica
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @PathVariable String id
    ) {
        servicoEsp32.excluir(id);

        return ResponseEntity.noContent().build();
    }
}
