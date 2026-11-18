package br.com.smartberry.controller;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.repository.RepositorioComandoEsp32;
import br.com.smartberry.repository.RepositorioDadosSensor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Comparator;
import java.util.Map;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class ControladorAlertas {

    private final RepositorioDadosSensor repositorioDadosSensor;
    private final RepositorioComandoEsp32 repositorioComandoEsp32;

    @GetMapping("/dht11")
    public ResponseEntity<?> obterUltimaLeituraDHT11() {
        Optional<DadosSensor> opt = repositorioDadosSensor.findAll()
                .stream()
                .max(Comparator.comparing(DadosSensor::getDataRegistro));
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DadosSensor ds = opt.get();
        Map<String, Object> resp = Map.of(
                "temperatura", ds.getTemperatura(),
                "umidade", ds.getUmidade(),
                "dataHora", ds.getDataRegistro()
        );
        return ResponseEntity.ok(resp);
    }

    private boolean estaLigadoIrrigacao() {
        return repositorioComandoEsp32.findByStatus(StatusComando.EXECUTADO)
                .stream()
                .filter(c -> c.getTipo() == TipoComando.ACIONAR_IRRIGACAO)
                .max(Comparator.comparing(ComandoEsp32::getDataCriacao))
                .map(ComandoEsp32::getValor)
                .map(v -> v != null && v > 0)
                .orElse(false);
    }

    @GetMapping("/irrigacao")
    public ResponseEntity<?> obterEstadoIrrigacao() {
        boolean ligada = estaLigadoIrrigacao();
        return ResponseEntity.ok(Map.of("ligada", ligada));
    }

    private boolean estaLigadoVentilacao() {
        return repositorioComandoEsp32.findByStatus(StatusComando.EXECUTADO)
                .stream()
                .filter(c -> c.getTipo() == TipoComando.LIGAR_VENTILACAO || c.getTipo() == TipoComando.DESLIGAR_VENTILACAO)
                .max(Comparator.comparing(ComandoEsp32::getDataCriacao))
                .map(c -> c.getTipo() == TipoComando.LIGAR_VENTILACAO)
                .orElse(false);
    }

    @GetMapping("/ventilacao")
    public ResponseEntity<?> obterEstadoVentilacao() {
        boolean ligada = estaLigadoVentilacao();
        return ResponseEntity.ok(Map.of("ligada", ligada));
    }
}
