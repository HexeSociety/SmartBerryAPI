package br.com.smartberry.service;

import br.com.smartberry.dto.PontoGrafico;
import br.com.smartberry.dto.ResumoMonitoramento;
import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.repository.RepositorioDadosSensor;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicoMonitoramento {

    private final RepositorioDadosSensor repositorioDadosSensor;
    private final ServicoEsp32 servicoEsp32;

    // Dashboard com dados atuais e estatísticas
    public ResumoMonitoramento consultarAtual(String esp32Id) {

        DispositivoEsp32 dispositivo =
                servicoEsp32.buscarPorId(esp32Id);

        DadosSensor ultima = repositorioDadosSensor
                .findFirstByEsp32IdOrderByDataRegistroDesc(esp32Id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Nenhuma leitura encontrada"
                ));

        LocalDateTime agora = LocalDateTime.now();

        List<DadosSensor> leituras = consultarPeriodo(
                esp32Id,
                agora.minusHours(24),
                agora
        );

        List<DadosSensor> validas = leituras.stream()
                .filter(this::leituraValida)
                .toList();

        DoubleSummaryStatistics temperaturas =
                validas.stream()
                        .mapToDouble(DadosSensor::getTemperatura)
                        .summaryStatistics();

        DoubleSummaryStatistics umidades =
                validas.stream()
                        .mapToDouble(DadosSensor::getUmidade)
                        .summaryStatistics();

        boolean online = ultima.getDataRegistro() != null
                && !ultima.getDataRegistro()
                .isBefore(agora.minusMinutes(3));

        return new ResumoMonitoramento(
                esp32Id,
                dispositivo.getNome(),

                ultima.getTemperatura(),
                ultima.getUmidade(),

                temperaturas.getCount() == 0
                        ? null : temperaturas.getMin(),
                temperaturas.getCount() == 0
                        ? null : temperaturas.getMax(),
                temperaturas.getCount() == 0
                        ? null : arredondar(temperaturas.getAverage()),

                umidades.getCount() == 0
                        ? null : umidades.getMin(),
                umidades.getCount() == 0
                        ? null : umidades.getMax(),
                umidades.getCount() == 0
                        ? null : arredondar(umidades.getAverage()),

                validas.size(),
                ultima.getDataRegistro(),
                online
        );
    }

    // Gráfico agrupado por hora
    public List<PontoGrafico> consultarGrafico(
            String esp32Id,
            int horas
    ) {
        if (horas < 1 || horas > 168) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe um período entre 1 e 168 horas"
            );
        }

        LocalDateTime agora = LocalDateTime.now();

        List<DadosSensor> leituras = consultarPeriodo(
                esp32Id,
                agora.minusHours(horas),
                agora
        );

        return agruparLeituras(leituras, false);
    }

    public List<PontoGrafico> consultarGraficoHoje(String esp32Id) {
        LocalDateTime agora = LocalDateTime.now();
        return agruparLeituras(consultarPeriodo(esp32Id,
                agora.toLocalDate().atStartOfDay(), agora), false);
    }

    // Histórico agrupado por dia
    public List<PontoGrafico> consultarHistorico(
            String esp32Id,
            int dias
    ) {
        if (dias < 1 || dias > 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Informe um período entre 1 e 30 dias"
            );
        }

        LocalDateTime agora = LocalDateTime.now();

        List<DadosSensor> leituras = consultarPeriodo(
                esp32Id,
                agora.minusDays(dias),
                agora
        );

        return agruparLeituras(leituras, true);
    }

    // Consulta MongoDB por período e dispositivo
    private List<DadosSensor> consultarPeriodo(
            String esp32Id,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        servicoEsp32.buscarPorId(esp32Id);

        return repositorioDadosSensor
                .findByEsp32IdAndDataRegistroBetweenOrderByDataRegistroAsc(
                        esp32Id,
                        inicio,
                        fim
                );
    }

    // Agrupa por hora ou por dia
    private List<PontoGrafico> agruparLeituras(
            List<DadosSensor> leituras,
            boolean porDia
    ) {
        Map<LocalDateTime, List<DadosSensor>> grupos =
                leituras.stream()
                        .filter(this::leituraValida)
                        .collect(Collectors.groupingBy(
                                leitura -> {
                                    LocalDateTime data =
                                            leitura.getDataRegistro();

                                    if (porDia) {
                                        return data.toLocalDate()
                                                .atStartOfDay();
                                    }

                                    return data.truncatedTo(
                                            ChronoUnit.HOURS
                                    );
                                },
                                TreeMap::new,
                                Collectors.toList()
                        ));

        return grupos.entrySet().stream()
                .map(grupo -> {

                    List<DadosSensor> registros =
                            grupo.getValue();

                    double mediaTemperatura =
                            registros.stream()
                                    .mapToDouble(
                                            DadosSensor::getTemperatura
                                    )
                                    .average()
                                    .orElse(0);

                    double mediaUmidade =
                            registros.stream()
                                    .mapToDouble(
                                            DadosSensor::getUmidade
                                    )
                                    .average()
                                    .orElse(0);

                    return new PontoGrafico(
                            grupo.getKey(),
                            arredondar(mediaTemperatura),
                            arredondar(mediaUmidade),
                            registros.size()
                    );
                })
                .toList();
    }

    // Ignora registros incompletos ou inválidos
    private boolean leituraValida(DadosSensor leitura) {
        return leitura.getDataRegistro() != null
                && leitura.getTemperatura() != null
                && leitura.getUmidade() != null
                && Double.isFinite(leitura.getTemperatura())
                && Double.isFinite(leitura.getUmidade());
    }

    // Uma casa decimal
    private double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
