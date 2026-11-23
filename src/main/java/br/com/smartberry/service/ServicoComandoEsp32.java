package br.com.smartberry.service;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.repository.RepositorioComandoEsp32;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
        comando.setStatus(StatusComando.PENDENTE);
        comando.setDataCriacao(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        return repositorioComandoEsp32.save(comando);
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
        validarDispositivoAtivo(esp32Id);
        Optional<ComandoEsp32> proximo;
        LocalDateTime limite = LocalDateTime.now().minusSeconds(30);
        while ((proximo = repositorioComandoEsp32.findFirstByEsp32IdAndStatusOrderByDataCriacaoAscIdAsc(
                esp32Id, StatusComando.PENDENTE)).isPresent()) {
            ComandoEsp32 comando = proximo.get();
            if (comando.getDataCriacao() != null && !comando.getDataCriacao().isBefore(limite)) {
                return proximo;
            }
            concluir(comando.getId(), StatusComando.REJEITADO);
        }
        return Optional.empty();
    }

    public List<ComandoEsp32> historicoIrrigacao(String esp32Id, int dias) {
        if (dias < 1 || dias > 30) {
            throw new IllegalArgumentException("Informe um período entre 1 e 30 dias");
        }
        LocalDateTime inicio = dias == 1 ? LocalDateTime.now().toLocalDate().atStartOfDay()
                : LocalDateTime.now().minusDays(dias);
        return repositorioComandoEsp32
                .findByEsp32IdAndTipoAndStatusAndDataConclusaoGreaterThanEqualOrderByDataConclusaoDesc(
                        esp32Id, TipoComando.ACIONAR_IRRIGACAO, StatusComando.EXECUTADO, inicio);
    }

    private void validarDispositivoAtivo(String esp32Id) {
        if (!servicoEsp32.buscarPorId(esp32Id).isAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ESP32 está desativado");
        }
    }

    private void validarComando(ComandoEsp32 comando) {
        if (comando.getTipo() == null) {
            throw new IllegalArgumentException("O tipo do comando é obrigatório");
        }
        switch (comando.getTipo()) {
            case ACIONAR_IRRIGACAO -> validarDuracaoIrrigacao(comando.getValor());
            case ALTERAR_INTERVALO -> validarIntervalo(comando.getValor());
            case PARAR_IRRIGACAO, LIGAR_VENTILACAO, DESLIGAR_VENTILACAO -> { }
        }
    }

    private void validarDuracaoIrrigacao(Integer duracao) {
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