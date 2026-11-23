package br.com.smartberry.service;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.repository.RepositorioComandoEsp32;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServicoComandoEsp32 {

    private static final int DURACAO_MAXIMA_IRRIGACAO_SEGUNDOS = 300;

    private final RepositorioComandoEsp32 repositorioComandoEsp32;
    private final ServicoEsp32 servicoEsp32;

    public ComandoEsp32 criarComando(ComandoEsp32 comando) {
        validarComando(comando);
        if (comando.getEsp32Id() != null) validarDispositivoAtivo(comando.getEsp32Id());
        comando.setId(null);
        comando.setDataConclusao(null);
        comando.setProgramadoPara(null);
        comando.setExpiraEm(null);
        comando.setAgendamentoDiario(false);
        if (comando.getTipo() == TipoComando.ACIONAR_VENTILACAO_TEMPORIZADA) {
            throw new IllegalArgumentException("Use o endpoint de agendamentos para ventilação temporizada");
        }
        comando.setStatus(StatusComando.PENDENTE);
        comando.setDataCriacao(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        return repositorioComandoEsp32.save(comando);
    }

    public void enfileirarAgendamento(String agendamentoId, String esp32Id, int duracao,
                                     Instant programadoPara, Instant expiraEm,
                                     TipoComando tipo, boolean diario) {
        validarDuracaoIrrigacao(duracao);
        ComandoEsp32 comando = new ComandoEsp32();
        comando.setId(idComandoAgendado(agendamentoId));
        comando.setEsp32Id(esp32Id);
        comando.setTipo(tipo);
        comando.setAgendamentoDiario(diario);
        comando.setValor(duracao);
        comando.setProgramadoPara(programadoPara);
        comando.setExpiraEm(expiraEm);
        comando.setStatus(StatusComando.PENDENTE);
        comando.setDataCriacao(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            repositorioComandoEsp32.insert(comando);
        } catch (DuplicateKeyException repeticao) {
            // A identidade do comando é fixa. Uma retentativa preserva o primeiro registro e seu ACK.
            if (!repositorioComandoEsp32.existsById(comando.getId())) throw repeticao;
        }
    }

    public List<ComandoEsp32> buscarComandosAgendados(List<String> ids) {
        return repositorioComandoEsp32.findAllById(ids);
    }

    public static String idComandoAgendado(String agendamentoId) {
        return "agendamento-" + agendamentoId;
    }

    public List<ComandoEsp32> listarPendentes() {
        return repositorioComandoEsp32.findByStatus(StatusComando.PENDENTE).stream()
                .filter(comando -> comando.getEsp32Id() == null).toList();
    }

    public ComandoEsp32 marcarComoExecutado(String id) {

        return concluir(id, StatusComando.EXECUTADO);
    }

    public ComandoEsp32 buscar(String id) {
        return repositorioComandoEsp32.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comando não encontrado"));
    }

    public synchronized ComandoEsp32 concluir(String id, StatusComando status) {
        ComandoEsp32 comando = buscar(id);
        if (comando.getStatus() != StatusComando.PENDENTE) {
            return comando;
        }
        comando.setStatus(status);
        comando.setDataConclusao(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        return repositorioComandoEsp32.save(comando);
    }

    public synchronized Optional<ComandoEsp32> proximo(String esp32Id) {
        DispositivoEsp32 dispositivo = validarDispositivoAtivo(esp32Id);
        Optional<ComandoEsp32> proximo;
        LocalDateTime limite = LocalDateTime.now().minusSeconds(30);
        while ((proximo = repositorioComandoEsp32.findFirstByEsp32IdAndStatusOrderByDataCriacaoAscIdAsc(
                esp32Id, StatusComando.PENDENTE)).isPresent()) {
            ComandoEsp32 comando = proximo.get();
            boolean disponivel = comando.getProgramadoPara() == null
                    ? comando.getDataCriacao() != null && !comando.getDataCriacao().isBefore(limite)
                    : Instant.now().isBefore(comando.getExpiraEm())
                        && permiteDiario(dispositivo, comando) && !conflitaComControleManual(comando, limite);
            if (disponivel) {
                return proximo;
            }
            concluir(comando.getId(), StatusComando.REJEITADO);
        }
        return Optional.empty();
    }

    private boolean permiteDiario(DispositivoEsp32 dispositivo, ComandoEsp32 comando) {
        if (!comando.isAgendamentoDiario()) return true;
        return comando.getTipo() == TipoComando.ACIONAR_IRRIGACAO
                ? dispositivo.isIrrigacaoDiaria() : dispositivo.isVentilacaoDiaria();
    }

    private boolean conflitaComControleManual(ComandoEsp32 comando, LocalDateTime limite) {
        boolean irrigacao = comando.getTipo() == TipoComando.ACIONAR_IRRIGACAO;
        TipoComando iniciar = irrigacao ? TipoComando.ACIONAR_IRRIGACAO : TipoComando.LIGAR_VENTILACAO;
        TipoComando parar = irrigacao ? TipoComando.PARAR_IRRIGACAO : TipoComando.DESLIGAR_VENTILACAO;
        String esp32Id = comando.getEsp32Id();
        LocalDateTime horario = LocalDateTime.ofInstant(comando.getProgramadoPara(), ZoneId.systemDefault());
        return repositorioComandoEsp32
                .existsByEsp32IdAndTipoInAndStatusAndProgramadoParaIsNullAndDataCriacaoGreaterThanEqual(
                        esp32Id, List.of(iniciar, parar),
                        StatusComando.PENDENTE, limite)
                || repositorioComandoEsp32.existsByEsp32IdAndTipoAndStatusInAndDataCriacaoGreaterThanEqual(
                        esp32Id, parar,
                        List.of(StatusComando.PENDENTE, StatusComando.EXECUTADO), horario);
    }

    public List<ComandoEsp32> historicoIrrigacao(String esp32Id, int dias) {
        if (dias < 1 || dias > 30) {
            throw new IllegalArgumentException("Informe um período entre 1 e 30 dias");
        }
        LocalDateTime inicio = dias == 1 ? LocalDateTime.now().toLocalDate().atStartOfDay()
                : LocalDateTime.now().minusDays(dias);
        return repositorioComandoEsp32
                .findByEsp32IdAndTipoAndStatusAndProgramadoParaIsNullAndDataConclusaoGreaterThanEqualOrderByDataConclusaoDesc(
                        esp32Id, TipoComando.ACIONAR_IRRIGACAO, StatusComando.EXECUTADO, inicio);
    }

    private DispositivoEsp32 validarDispositivoAtivo(String esp32Id) {
        DispositivoEsp32 dispositivo = servicoEsp32.buscarPorId(esp32Id);
        if (!dispositivo.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ESP32 está desativado");
        }
        return dispositivo;
    }

    private void validarComando(ComandoEsp32 comando) {
        if (comando.getTipo() == null) {
            throw new IllegalArgumentException("O tipo do comando é obrigatório");
        }
        switch (comando.getTipo()) {
            case ACIONAR_IRRIGACAO, ACIONAR_VENTILACAO_TEMPORIZADA -> validarDuracaoIrrigacao(comando.getValor());
            case ALTERAR_INTERVALO -> validarIntervalo(comando.getValor());
            case CONFIGURAR_IRRIGACAO_AUTOMATICA -> {
                if (comando.getEsp32Id() == null || comando.getValor() == null
                        || (comando.getValor() != 0 && comando.getValor() != 1)) {
                    throw new IllegalArgumentException("Informe o ESP32 e um valor de modo automático igual a 0 ou 1");
                }
            }
            case PARAR_IRRIGACAO, LIGAR_VENTILACAO, DESLIGAR_VENTILACAO -> { }
        }
    }

    public void validarDuracaoIrrigacao(Integer duracao) {
        if (duracao == null || duracao <= 0) {
            throw new IllegalArgumentException("A duração da irrigação deve ser maior que zero");
        }
        if (duracao > DURACAO_MAXIMA_IRRIGACAO_SEGUNDOS) {
            throw new IllegalArgumentException(
                    "A duração máxima da irrigação é de " + DURACAO_MAXIMA_IRRIGACAO_SEGUNDOS + " segundos");
        }
    }

    private void validarIntervalo(Integer intervalo) {
        if (intervalo == null || intervalo <= 0) {
            throw new IllegalArgumentException("O intervalo deve ser maior que zero");
        }
    }
}