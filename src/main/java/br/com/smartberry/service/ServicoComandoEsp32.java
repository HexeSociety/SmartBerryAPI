package br.com.smartberry.service;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import br.com.smartberry.model.TipoComando;
import br.com.smartberry.repository.RepositorioComandoEsp32;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoComandoEsp32 {

    private static final int DURACAO_MAXIMA_IRRIGACAO_SEGUNDOS = 300;

    private final RepositorioComandoEsp32 repositorioComandoEsp32;

    public ComandoEsp32 criarComando(ComandoEsp32 comando) {
        validarComando(comando);
        comando.setStatus(StatusComando.PENDENTE);
        comando.setDataCriacao(LocalDateTime.now());
        return repositorioComandoEsp32.save(comando);
    }

    public List<ComandoEsp32> listarPendentes() {
        return repositorioComandoEsp32.findByStatus(StatusComando.PENDENTE);
    }

    private void validarComando(ComandoEsp32 comando) {
        if (comando.getTipo() == null) {
            throw new IllegalArgumentException("O tipo do comando é obrigatório");
        }
        switch (comando.getTipo()) {
            case ACIONAR_IRRIGACAO -> validarDuracaoIrrigacao(comando.getValor());
            case ALTERAR_INTERVALO -> validarIntervalo(comando.getValor());
            case PARAR_IRRIGACAO -> { }
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