package br.com.smartberry.service;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.repository.RepositorioDadosSensor;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ServicoDadosSensor {

    private final RepositorioDadosSensor repositorioDadosSensor;
    private final ServicoEsp32 servicoEsp32;

    public DadosSensor salvarDados(DadosSensor dadosSensor) {
        dadosSensor.setDataRegistro(LocalDateTime.now());
        return repositorioDadosSensor.save(dadosSensor);
    }
    // Recebe uma leitura vinculada ao ESP32
    public DadosSensor salvarLeituraEsp32(
            String esp32Id,
            DadosSensor entrada
    ) {
        DispositivoEsp32 dispositivo =
                servicoEsp32.buscarPorId(esp32Id);

        if (!dispositivo.isAtivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O ESP32 está desativado"
            );
        }

        if (entrada == null ||
                entrada.getTemperatura() == null ||
                entrada.getUmidade() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Temperatura e umidade são obrigatórias"
            );
        }

        Double temperatura = entrada.getTemperatura();
        Double umidade = entrada.getUmidade();

        if (!Double.isFinite(temperatura) ||
                !Double.isFinite(umidade) ||
                umidade < 0 || umidade > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valores de leitura inválidos"
            );
        }

        DadosSensor leitura = new DadosSensor();

        leitura.setEsp32Id(dispositivo.getId());
        leitura.setTemperatura(temperatura);
        leitura.setUmidade(umidade);
        leitura.setIrrigacaoLigada(entrada.getIrrigacaoLigada());
        leitura.setVentilacaoLigada(entrada.getVentilacaoLigada());
        leitura.setIrrigacaoAutomatica(entrada.getIrrigacaoAutomatica());
        leitura.setVentilacaoAutomatica(entrada.getVentilacaoAutomatica());
        leitura.setDataRegistro(LocalDateTime.now());

        return repositorioDadosSensor.save(leitura);
    }
    // Retorna a última leitura do dispositivo
    public DadosSensor buscarUltimaLeitura(String esp32Id) {
        servicoEsp32.buscarPorId(esp32Id);

        return repositorioDadosSensor
                .findFirstByEsp32IdOrderByDataRegistroDesc(esp32Id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Nenhuma leitura encontrada"
                ));
    }
    // Retorna o histórico do dispositivo
    public List<DadosSensor> buscarHistorico(
            String esp32Id,
            int limite
    ) {
        servicoEsp32.buscarPorId(esp32Id);

        if (limite < 1 || limite > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O limite deve estar entre 1 e 200"
            );
        }

        return repositorioDadosSensor
                .findByEsp32IdOrderByDataRegistroDesc(
                        esp32Id,
                        PageRequest.of(0, limite)
                );
    }

}