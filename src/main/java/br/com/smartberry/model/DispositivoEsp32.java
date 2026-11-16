package br.com.smartberry.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "dispositivos_esp32")
public class DispositivoEsp32 {

    @Id
    private String id;

    private String codigo;
    private String nome;
    private String estufaId;

    private boolean ativo;
    private boolean excluido;
}
