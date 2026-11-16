package br.com.smartberry.dto;

import java.time.LocalDateTime;

public record PontoGrafico(
        LocalDateTime periodo,
        Double temperaturaMedia,
        Double umidadeMedia,
        long totalLeituras
) {}
