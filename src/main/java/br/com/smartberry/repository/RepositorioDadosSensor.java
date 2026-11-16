package br.com.smartberry.repository;

import br.com.smartberry.model.DadosSensor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;

@Repository
public interface RepositorioDadosSensor
        extends MongoRepository<DadosSensor, String> {
    // Busca a leitura mais recente do ESP32
    Optional<DadosSensor> findFirstByEsp32IdOrderByDataRegistroDesc(
            String esp32Id
    );
    // Busca o histórico de um ESP32
    List<DadosSensor> findByEsp32IdOrderByDataRegistroDesc(
            String esp32Id,
            Pageable pageable
    );
}