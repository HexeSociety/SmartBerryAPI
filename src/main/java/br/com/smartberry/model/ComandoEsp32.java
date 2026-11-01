package br.com.smartberry.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "comandos_esp32")
public class ComandoEsp32 {
    @Id
    private String id;
    private TipoComando tipo;
    private Integer valor;
    private StatusComando status;
    private LocalDateTime dataCriacao;
}