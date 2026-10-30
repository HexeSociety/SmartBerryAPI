package br.com.smartberry.repository;

import br.com.smartberry.model.DadosSensor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositorioDadosSensor extends MongoRepository<DadosSensor, String> {
}