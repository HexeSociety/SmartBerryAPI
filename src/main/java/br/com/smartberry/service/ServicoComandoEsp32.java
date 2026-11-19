package br.com.smartberry.service;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.repository.RepositorioComandoEsp32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ServicoComandoEsp32 {
    private static final int DURACAO_MAXIMA_IRRIGACAO_SEGUNDOS = 300;
    private static final int VALIDADE_COMANDO_SEGUNDOS = 120;

    private final RepositorioComandoEsp32 repositorioComandoEsp32;
    private final MongoTemplate mongoTemplate;
    private final String dispositivoConfigurado;

    public ServicoComandoEsp32(RepositorioComandoEsp32 repositorioComandoEsp32,
                              MongoTemplate mongoTemplate,
                              @Value("${SMARTBERRY_IOT_DEVICE_ID:}") String dispositivoConfigurado) {
        this.repositorioComandoEsp32 = repositorioComandoEsp32;
        this.mongoTemplate = mongoTemplate;
        this.dispositivoConfigurado = dispositivoConfigurado;
    }

    public ComandoEsp32 criarComando(ComandoEsp32 pedido) {
        validarComando(pedido);
        if (dispositivoConfigurado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ESP32 IoT nao configurado");
        }
        // Compatibilidade com o Android atual: sem ID, usa o unico ESP32 configurado.
        String esp32Id = pedido.getEsp32Id() == null || pedido.getEsp32Id().isBlank()
                ? dispositivoConfigurado : pedido.getEsp32Id();
        if (!dispositivoConfigurado.equals(esp32Id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ESP32 nao autorizado para esta integracao");
        }
        verificarDispositivoAtivo(esp32Id);

        // Nunca reaproveita ID, status ou datas enviados pelo cliente.
        ComandoEsp32 comando = new ComandoEsp32();
        comando.setEsp32Id(esp32Id);
        comando.setTipo(pedido.getTipo());
        comando.setValor(pedido.getValor());
        comando.setStatus(StatusComando.PENDENTE);
        comando.setDataCriacao(LocalDateTime.now());
        comando.setDataExpiracao(Instant.now().plusSeconds(VALIDADE_COMANDO_SEGUNDOS));
        return repositorioComandoEsp32.save(comando);
    }

    public List<ComandoEsp32> listarPendentes(String esp32Id) {
        verificarDispositivoAtivo(esp32Id);
        return repositorioComandoEsp32
                .findTop20ByEsp32IdAndStatusAndDataExpiracaoAfterOrderByDataCriacaoAscIdAsc(
                        esp32Id, StatusComando.PENDENTE, Instant.now());
    }

    public ComandoEsp32 marcarComoExecutado(String id, String esp32Id) {
        return finalizar(id, esp32Id, StatusComando.EXECUTADO);
    }

    public ComandoEsp32 marcarComoFalhou(String id, String esp32Id) {
        return finalizar(id, esp32Id, StatusComando.FALHOU);
    }

    private ComandoEsp32 finalizar(String id, String esp32Id, StatusComando novoStatus) {
        // Atualizacao atomica: somente o comando PENDENTE deste dispositivo muda de estado.
        // Uma confirmacao tardia e permitida caso a execucao ja tenha ocorrido.
        Query query = Query.query(Criteria.where("id").is(id)
                .and("esp32Id").is(esp32Id).and("status").is(StatusComando.PENDENTE));
        Update update = new Update().set("status", novoStatus).set("dataFinalizacao", Instant.now());
        ComandoEsp32 atualizado = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ComandoEsp32.class);
        if (atualizado != null) return atualizado;

        // Se a resposta anterior se perdeu, repetir a mesma confirmacao nao altera a data.
        ComandoEsp32 existente = repositorioComandoEsp32.findByIdAndEsp32Id(id, esp32Id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comando nao encontrado"));
        if (existente.getStatus() == novoStatus) return existente;
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Comando ja finalizado com outro status");
    }

    private void verificarDispositivoAtivo(String esp32Id) {
        Query query = Query.query(Criteria.where("id").is(esp32Id)
                .and("ativo").is(true).and("excluido").ne(true));
        if (!mongoTemplate.exists(query, DispositivoEsp32.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ESP32 inexistente, inativo ou excluido");
        }
    }

    private void validarComando(ComandoEsp32 comando) {
        if (comando == null || comando.getTipo() == null) {
            throw new IllegalArgumentException("O tipo do comando e obrigatorio");
        }
        switch (comando.getTipo()) {
            case ACIONAR_IRRIGACAO -> validarDuracaoIrrigacao(comando.getValor());
            case ALTERAR_INTERVALO -> validarIntervalo(comando.getValor());
            case PARAR_IRRIGACAO, LIGAR_VENTILACAO, DESLIGAR_VENTILACAO -> { }
        }
    }

    private void validarDuracaoIrrigacao(Integer duracao) {
        if (duracao == null || duracao <= 0 || duracao > DURACAO_MAXIMA_IRRIGACAO_SEGUNDOS) {
            throw new IllegalArgumentException("A duracao da irrigacao deve ser entre 1 e 300 segundos");
        }
    }

    private void validarIntervalo(Integer intervalo) {
        if (intervalo == null || intervalo <= 0) {
            throw new IllegalArgumentException("O intervalo deve ser maior que zero");
        }
    }
}
