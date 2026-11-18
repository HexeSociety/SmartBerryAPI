package br.com.smartberry.dto;

import java.time.LocalDateTime;

public record ResumoMonitoramento(
        String esp32Id,
        String nomeDispositivo,

        Double temperaturaAtual,
        Double umidadeAtual,

        Double temperaturaMinima24h,
        Double temperaturaMaxima24h,
        Double temperaturaMedia24h,

        Double umidadeMinima24h,
        Double umidadeMaxima24h,
        Double umidadeMedia24h,

        long totalLeituras24h,
        LocalDateTime ultimaLeitura,
        boolean online
) {}
