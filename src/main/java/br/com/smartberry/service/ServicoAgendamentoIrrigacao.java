package br.com.smartberry.service;

import br.com.smartberry.model.AgendamentoIrrigacao;
import br.com.smartberry.model.AgendamentoIrrigacao.Estado;
import br.com.smartberry.model.AgendamentoIrrigacao.Equipamento;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.repository.RepositorioAgendamentoIrrigacao;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicoAgendamentoIrrigacao {

    private static final long JANELA_SEGUNDOS = 30;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final MongoTemplate mongo;
    private final RepositorioAgendamentoIrrigacao agendamentos;
    private final ServicoComandoEsp32 comandos;
    private final ServicoEsp32 dispositivos;

    public record Resumo(String id, String esp32Id, LocalDateTime horario, int duracaoSegundos,
                         String status, boolean diario) {}

    public Resumo criar(String esp32Id, Equipamento equipamento, LocalDateTime horario, Integer duracao, boolean diario) {
        DispositivoEsp32 dispositivo = dispositivos.buscarPorId(esp32Id);
        if (!dispositivo.isAtivo()) throw conflito("O ESP32 está desativado");
        Instant programado = validar(horario, duracao);
        return resumir(agendamentos.insert(new AgendamentoIrrigacao(UUID.randomUUID().toString(),
                esp32Id, programado, duracao, Estado.PENDENTE, equipamento, diario)), null, Instant.now());
    }

    private Instant validar(LocalDateTime horario, Integer duracao) {
        comandos.validarDuracaoIrrigacao(duracao);
        if (horario == null || FUSO.getRules().getValidOffsets(horario).size() != 1) {
            throw new IllegalArgumentException("Informe uma data e horário válidos de São Paulo");
        }
        Instant programado = horario.atZone(FUSO).toInstant();
        if (!programado.isAfter(Instant.now())) throw new IllegalArgumentException("Escolha um horário futuro");
        return programado;
    }

    public List<Resumo> listar(String esp32Id, Equipamento equipamento) {
        DispositivoEsp32 dispositivo = dispositivos.buscarPorId(esp32Id);
        List<AgendamentoIrrigacao> lista = agendamentos.findByEsp32IdOrderByProgramadoParaAscIdAsc(esp32Id)
                .stream().filter(item -> item.equipamentoEfetivo() == equipamento).toList();
        var porId = comandos.buscarComandosAgendados(lista.stream().map(this::idComando).toList())
                .stream().collect(Collectors.toMap(ComandoEsp32::getId, Function.identity()));
        Instant agora = Instant.now();
        return lista.stream().map(item -> {
            if (item.diario() && item.estado() == Estado.PENDENTE && !modo(dispositivo, equipamento)) {
                return new Resumo(item.id(), esp32Id, LocalDateTime.ofInstant(item.programadoPara(), FUSO),
                        item.duracaoSegundos(), "PAUSADO", true);
            }
            return resumir(item, porId.get(idComando(item)), agora);
        }).toList();
    }

    public Resumo editar(String esp32Id, Equipamento equipamento, String id,
                          LocalDateTime horario, Integer duracao, boolean diario) {
        AgendamentoIrrigacao item = buscar(esp32Id, equipamento, id);
        Instant programado = validar(horario, duracao);
        AgendamentoIrrigacao atualizado = mongo.findAndModify(porOcorrencia(item, Estado.PENDENTE),
                Update.update("programadoPara", programado).set("duracaoSegundos", duracao).set("diario", diario),
                FindAndModifyOptions.options().returnNew(true), AgendamentoIrrigacao.class);
        if (atualizado == null) throw conflito("Somente horários pendentes podem ser editados");
        return resumir(atualizado, null, Instant.now());
    }

    public void cancelar(String esp32Id, Equipamento equipamento, String id) {
        AgendamentoIrrigacao item = buscar(esp32Id, equipamento, id);
        if (item.estado() == Estado.CANCELADO
                || alterarEstado(porOcorrencia(item, Estado.PENDENTE), Estado.CANCELADO)
                || alterarEstado(porOcorrencia(item, Estado.EXPIRADO), Estado.CANCELADO)
                || (!item.diario() && !Instant.now().isBefore(expiracao(item))
                    && alterarEstado(porOcorrencia(item, Estado.DISPARADO), Estado.CANCELADO))) return;
        throw conflito("Aguarde a janela de execução para excluir este horário");
    }

    private AgendamentoIrrigacao buscar(String esp32Id, Equipamento equipamento, String id) {
        return agendamentos.findById(id)
                .filter(item -> item.esp32Id().equals(esp32Id) && item.equipamentoEfetivo() == equipamento)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado"));
    }

    public boolean modo(String esp32Id, Equipamento equipamento) {
        return modo(dispositivos.buscarPorId(esp32Id), equipamento);
    }

    private boolean modo(DispositivoEsp32 dispositivo, Equipamento equipamento) {
        return equipamento == Equipamento.IRRIGACAO ? dispositivo.isIrrigacaoDiaria() : dispositivo.isVentilacaoDiaria();
    }

    public void alterarModo(String esp32Id, Equipamento equipamento, boolean ativo) {
        dispositivos.buscarPorId(esp32Id);
        String campo = equipamento == Equipamento.IRRIGACAO ? "irrigacaoDiaria" : "ventilacaoDiaria";
        if (mongo.updateFirst(Query.query(where("id").is(esp32Id).and("ativo").is(true).and("excluido").ne(true)),
                Update.update(campo, ativo), DispositivoEsp32.class).getMatchedCount() == 0) {
            throw conflito("O ESP32 está desativado");
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void processar() {
        Instant agora = Instant.now();
        agendamentos.findByEstadoAndProgramadoParaLessThanEqual(Estado.DISPARANDO, agora)
                .forEach(this::enfileirar);
        agendamentos.findByEstadoAndProgramadoParaLessThanEqualAndDiarioTrue(Estado.DISPARADO,
                agora.minusSeconds(JANELA_SEGUNDOS)).forEach(item -> avancar(item, Estado.DISPARADO, agora));
        for (AgendamentoIrrigacao item : agendamentos.findByEstadoAndProgramadoParaLessThanEqual(Estado.PENDENTE, agora)) {
            if (item.diario() && (!agora.isBefore(expiracao(item)) || !diarioHabilitado(item))) {
                avancar(item, Estado.PENDENTE, agora);
            } else if (!agora.isBefore(expiracao(item))) {
                alterarEstado(porOcorrencia(item, Estado.PENDENTE), Estado.EXPIRADO);
            } else {
                Query devido = Query.query(where("id").is(item.id()).and("estado").is(Estado.PENDENTE)
                        .and("programadoPara").is(item.programadoPara())
                        .and("duracaoSegundos").is(item.duracaoSegundos()));
                AgendamentoIrrigacao emProcessamento = mongo.findAndModify(devido,
                        Update.update("estado", Estado.DISPARANDO), FindAndModifyOptions.options().returnNew(true),
                        AgendamentoIrrigacao.class);
                if (emProcessamento != null) enfileirar(emProcessamento);
            }
        }
    }

    private boolean diarioHabilitado(AgendamentoIrrigacao item) {
        try {
            DispositivoEsp32 dispositivo = dispositivos.buscarPorId(item.esp32Id());
            return dispositivo.isAtivo() && modo(dispositivo, item.equipamentoEfetivo());
        } catch (ResponseStatusException erro) {
            if (erro.getStatusCode() == HttpStatus.NOT_FOUND) return false;
            throw erro;
        }
    }

    private void avancar(AgendamentoIrrigacao item, Estado origem, Instant agora) {
        var dataAtual = LocalDateTime.ofInstant(agora, FUSO);
        var agendado = LocalDateTime.ofInstant(item.programadoPara(), FUSO);
        var seguinte = dataAtual.toLocalDate().atTime(agendado.toLocalTime());
        if (!seguinte.isAfter(dataAtual)) seguinte = seguinte.plusDays(1);
        Query query = porOcorrencia(item, origem);
        query.addCriteria(where("diario").is(true));
        mongo.updateFirst(query, Update.update("estado", Estado.PENDENTE)
                .set("programadoPara", seguinte.atZone(FUSO).toInstant()), AgendamentoIrrigacao.class);
    }

    private void enfileirar(AgendamentoIrrigacao item) {
        try {
            TipoComando tipo = item.equipamentoEfetivo() == Equipamento.IRRIGACAO
                    ? TipoComando.ACIONAR_IRRIGACAO : TipoComando.ACIONAR_VENTILACAO_TEMPORIZADA;
            String ocorrencia = item.diario() ? item.id() + "-" + item.programadoPara().toEpochMilli() : item.id();
            comandos.enfileirarAgendamento(ocorrencia, item.esp32Id(), item.duracaoSegundos(),
                    item.programadoPara(), expiracao(item), tipo, item.diario());
            alterarEstado(porOcorrencia(item, Estado.DISPARANDO), Estado.DISPARADO);
        } catch (RuntimeException erro) {
            log.warn("Falha ao enfileirar agendamento {}. A API tentará novamente.", item.id(), erro);
        }
    }

    private String idComando(AgendamentoIrrigacao item) {
        return ServicoComandoEsp32.idComandoAgendado(
                item.diario() ? item.id() + "-" + item.programadoPara().toEpochMilli() : item.id());
    }

    private ResponseStatusException conflito(String motivo) {
        return new ResponseStatusException(HttpStatus.CONFLICT, motivo);
    }

    private Query porOcorrencia(AgendamentoIrrigacao item, Estado estado) {
        return Query.query(where("id").is(item.id()).and("estado").is(estado)
                .and("programadoPara").is(item.programadoPara()));
    }

    private boolean alterarEstado(Query query, Estado destino) {
        return mongo.updateFirst(query, Update.update("estado", destino), AgendamentoIrrigacao.class)
                .getModifiedCount() == 1;
    }

    private Instant expiracao(AgendamentoIrrigacao item) {
        return item.programadoPara().plusSeconds(JANELA_SEGUNDOS);
    }

    private Resumo resumir(AgendamentoIrrigacao item, ComandoEsp32 comando, Instant agora) {
        String status = item.estado().name();
        if (comando != null && item.estado() != Estado.CANCELADO) {
            status = comando.getStatus() == StatusComando.PENDENTE
                    ? agora.isBefore(expiracao(item)) ? "AGUARDANDO_PLACA" : "EXPIRADO"
                    : comando.getStatus() == StatusComando.REJEITADO && comando.getDataConclusao() != null
                        && !comando.getDataConclusao().atZone(ZoneId.systemDefault()).toInstant().isBefore(expiracao(item))
                        ? "EXPIRADO" : comando.getStatus().name();
        }
        return new Resumo(item.id(), item.esp32Id(), LocalDateTime.ofInstant(item.programadoPara(), FUSO),
                item.duracaoSegundos(), status, item.diario());
    }
}
