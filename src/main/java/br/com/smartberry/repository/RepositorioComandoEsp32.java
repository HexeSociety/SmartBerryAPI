package br.com.smartberry.repository;

import br.com.smartberry.model.ComandoEsp32;
import br.com.smartberry.model.StatusComando;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioComandoEsp32 extends MongoRepository<ComandoEsp32, String> {
    List<ComandoEsp32> findByStatus(StatusComando status);
}