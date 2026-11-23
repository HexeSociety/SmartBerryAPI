package br.com.smartberry.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Document(collection = "dados_sensor")
public class DadosSensor {

    @Id
    private String id;

    private String esp32Id;
    private Double temperatura;
    private Double umidade;
    private Boolean irrigacaoLigada;
    private Boolean ventilacaoLigada;
    private Boolean irrigacaoAutomatica;
    private Boolean ventilacaoAutomatica;
    private LocalDateTime dataRegistro;

    public Long getRegistroEpochMillis() {
        return dataRegistro == null ? null
                : dataRegistro.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
