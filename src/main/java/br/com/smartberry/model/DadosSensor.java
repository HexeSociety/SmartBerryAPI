package br.com.smartberry.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "dados_sensor")
public class DadosSensor {

    @Id
    private String id;
    private Double temperatura;
    private Double umidade;
    private LocalDateTime dataRegistro;

}