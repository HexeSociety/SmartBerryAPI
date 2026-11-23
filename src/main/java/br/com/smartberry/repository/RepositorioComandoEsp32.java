package br.com.smartberry.repository;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import br.com.smartberry.model.TipoComando;

@Repository
public interface RepositorioComandoEsp32 extends MongoRepository<ComandoEsp32, String> {
    List<ComandoEsp32> findByStatus(StatusComando status);
    Optional<ComandoEsp32> findFirstByEsp32IdAndStatusOrderByDataCriacaoAscIdAsc(
            String esp32Id, StatusComando status);
    List<ComandoEsp32> findByEsp32IdAndTipoAndStatusAndDataConclusaoGreaterThanEqualOrderByDataConclusaoDesc(
            String esp32Id, TipoComando tipo, StatusComando status,
            LocalDateTime inicio);
}