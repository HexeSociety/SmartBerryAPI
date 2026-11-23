package br.com.smartberry.repository;

import br.com.smartberry.model.AgendamentoIrrigacao;
import br.com.smartberry.model.AgendamentoIrrigacao.Estado;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RepositorioAgendamentoIrrigacao extends MongoRepository<AgendamentoIrrigacao, String> {
    List<AgendamentoIrrigacao> findByEsp32IdOrderByProgramadoParaAscIdAsc(String esp32Id);
    List<AgendamentoIrrigacao> findByEstadoAndProgramadoParaLessThanEqual(Estado estado, Instant agora);
    List<AgendamentoIrrigacao> findByEstadoAndProgramadoParaLessThanEqualAndDiarioTrue(Estado estado, Instant agora);
}
