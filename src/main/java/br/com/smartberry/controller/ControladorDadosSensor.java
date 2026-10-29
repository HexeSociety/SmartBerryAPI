package br.com.smartberry.controller;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.service.ServicoDadosSensor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensores")
@RequiredArgsConstructor
public class ControladorDadosSensor {

    private final ServicoDadosSensor servicoDadosSensor;

    @PostMapping
    public ResponseEntity<DadosSensor> receberDados(@RequestBody DadosSensor dadosSensor) {
        DadosSensor dadosSalvos = servicoDadosSensor.salvarDados(dadosSensor);
        return ResponseEntity.status(HttpStatus.CREATED).body(dadosSalvos);
    }
}