package br.com.smartberry.repository;

import br.com.smartberry.model.DispositivoEsp32;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioEsp32
        extends MongoRepository<DispositivoEsp32, String> {

    Optional<DispositivoEsp32> findByCodigo(String codigo);

    List<DispositivoEsp32> findByExcluidoFalse();
}
