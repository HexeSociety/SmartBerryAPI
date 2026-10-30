package br.com.smartberry.service;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.repository.RepositorioDadosSensor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ServicoDadosSensor {

    private final RepositorioDadosSensor repositorioDadosSensor;

    public DadosSensor salvarDados(DadosSensor dadosSensor) {
        dadosSensor.setDataRegistro(LocalDateTime.now());
        return repositorioDadosSensor.save(dadosSensor);
    }
}