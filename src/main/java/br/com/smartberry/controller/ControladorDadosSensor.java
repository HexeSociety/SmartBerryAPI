package br.com.smartberry.controller;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.service.ServicoDadosSensor;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensores")
@RequiredArgsConstructor
public class ControladorDadosSensor {

    private final ServicoDadosSensor servicoDadosSensor;

    // Endpoint original
    @PostMapping
    public ResponseEntity<DadosSensor> receberDados(
            @RequestBody DadosSensor dadosSensor
    ) {
        DadosSensor dadosSalvos =
                servicoDadosSensor.salvarDados(dadosSensor);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dadosSalvos);
    }

    // Recebe leituras identificadas pelo ESP32
    @PostMapping("/esp32/{esp32Id}")
    public ResponseEntity<DadosSensor> receberLeituraEsp32(
            @PathVariable String esp32Id,
            @RequestBody DadosSensor dadosSensor
    ) {
        DadosSensor leitura =
                servicoDadosSensor.salvarLeituraEsp32(
                        esp32Id, dadosSensor
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(leitura);
    }

    // Retorna a última leitura
    @GetMapping("/esp32/{esp32Id}/ultima")
    public ResponseEntity<DadosSensor> buscarUltimaLeitura(
            @PathVariable String esp32Id
    ) {
        return ResponseEntity.ok(
                servicoDadosSensor.buscarUltimaLeitura(esp32Id)
        );
    }

    // Retorna o histórico das leituras
    @GetMapping("/esp32/{esp32Id}/historico")
    public ResponseEntity<List<DadosSensor>> buscarHistorico(
            @PathVariable String esp32Id,
            @RequestParam(defaultValue = "50") int limite
    ) {
        return ResponseEntity.ok(
                servicoDadosSensor.buscarHistorico(
                        esp32Id, limite
                )
        );
    }
}
