package br.com.smartberry.repository;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepositorioComandoEsp32 extends MongoRepository<ComandoEsp32, String> {
    List<ComandoEsp32> findByStatus(StatusComando status);

    List<ComandoEsp32> findTop20ByEsp32IdAndStatusAndDataExpiracaoAfterOrderByDataCriacaoAscIdAsc(
            String esp32Id, StatusComando status, Instant agora);

    Optional<ComandoEsp32> findByIdAndEsp32Id(String id, String esp32Id);
}
