package br.com.smartberry.service;

import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.repository.RepositorioEsp32;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoEsp32 {

    private final RepositorioEsp32 repositorioEsp32;

    // CREATE - Cadastrar ESP32
    public DispositivoEsp32 cadastrar(DispositivoEsp32 dados) {
        validarDados(dados);

        String codigo = dados.getCodigo().trim();
        verificarCodigoDisponivel(codigo, null);

        DispositivoEsp32 dispositivo = new DispositivoEsp32();

        dispositivo.setCodigo(codigo);
        dispositivo.setNome(dados.getNome().trim());
        dispositivo.setEstufaId(dados.getEstufaId().trim());
        dispositivo.setAtivo(true);
        dispositivo.setExcluido(false);

        return repositorioEsp32.save(dispositivo);
    }

    // READ - Listar dispositivos
    public List<DispositivoEsp32> listar() {
        return repositorioEsp32.findByExcluidoFalse();
    }

    // READ - Buscar dispositivo pelo ID
    public DispositivoEsp32 buscarPorId(String id) {
        return repositorioEsp32.findById(id)
                .filter(dispositivo -> !dispositivo.isExcluido())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "ESP32 não encontrado"
                ));
    }

    // UPDATE - Atualizar dispositivo
    public DispositivoEsp32 atualizar(
            String id,
            DispositivoEsp32 dados
    ) {
        DispositivoEsp32 dispositivo = buscarPorId(id);

        validarDados(dados);

        String codigo = dados.getCodigo().trim();
        verificarCodigoDisponivel(codigo, id);

        dispositivo.setCodigo(codigo);
        dispositivo.setNome(dados.getNome().trim());
        dispositivo.setEstufaId(dados.getEstufaId().trim());
        dispositivo.setAtivo(dados.isAtivo());

        return repositorioEsp32.save(dispositivo);
    }

    // DELETE - Exclusão lógica
    public void excluir(String id) {
        DispositivoEsp32 dispositivo = buscarPorId(id);

        dispositivo.setAtivo(false);
        dispositivo.setExcluido(true);

        repositorioEsp32.save(dispositivo);
    }

    // Validação dos dados obrigatórios
    private void validarDados(DispositivoEsp32 dados) {

        if (dados == null ||
                dados.getCodigo() == null ||
                dados.getCodigo().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O código do ESP32 é obrigatório"
            );
        }

        if (dados.getNome() == null ||
                dados.getNome().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O nome do ESP32 é obrigatório"
            );
        }

        if (dados.getEstufaId() == null ||
                dados.getEstufaId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O ID da estufa é obrigatório"
            );
        }
    }

    // Evita cadastrar códigos repetidos
    private void verificarCodigoDisponivel(
            String codigo,
            String idIgnorado
    ) {
        repositorioEsp32.findByCodigo(codigo)
                .filter(dispositivo ->
                        !dispositivo.getId().equals(idIgnorado))
                .ifPresent(dispositivo -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Já existe um ESP32 com esse código"
                    );
                });
    }
}
