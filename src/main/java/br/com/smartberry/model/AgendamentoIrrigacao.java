package br.com.smartberry.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "agendamentos_irrigacao")
public record AgendamentoIrrigacao(
        @Id String id,
        String esp32Id,
        Instant programadoPara,
        int duracaoSegundos,
        Estado estado,
        Equipamento equipamento,
        Boolean diario) {

    public AgendamentoIrrigacao {
        diario = Boolean.TRUE.equals(diario);
    }

    public enum Equipamento { IRRIGACAO, VENTILACAO }

    public Equipamento equipamentoEfetivo() {
        return equipamento == null ? Equipamento.IRRIGACAO : equipamento;
    }

    public enum Estado {
        PENDENTE, CANCELADO, DISPARANDO, DISPARADO, EXPIRADO
    }
}
