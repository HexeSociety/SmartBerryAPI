package br.com.smartberry.service;

import br.com.smartberry.model.DadosSensor;
import br.com.smartberry.model.DispositivoEsp32;
import br.com.smartberry.repository.RepositorioDadosSensor;
import br.com.smartberry.repository.RepositorioEsp32;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoEsp32 {

    private final RepositorioEsp32 repositorioEsp32;
    private final RepositorioDadosSensor repositorioDadosSensor;

    public DispositivoEsp32 registrarAutomaticamente(String codigo) {
        if (!codigo.matches("esp32-[0-9a-f]{12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código do ESP32 inválido");
        }

        return repositorioEsp32.findByCodigo(codigo)
                .map(this::validarRegistroAutomatico)
                .orElseGet(() -> inserirRegistroAutomatico(codigo));
    }

    private DispositivoEsp32 inserirRegistroAutomatico(String codigo) {
        DispositivoEsp32 dispositivo = new DispositivoEsp32();
        dispositivo.setId(codigo);
        dispositivo.setCodigo(codigo);
        dispositivo.setNome("Estufa SmartBerry");
        dispositivo.setEstufaId("estufa-" + codigo);
        dispositivo.setAtivo(true);
        try {
            return repositorioEsp32.insert(dispositivo);
        } catch (DuplicateKeyException conflito) {
            return repositorioEsp32.findById(codigo)
                    .filter(existente -> codigo.equals(existente.getCodigo()))
                    .map(this::validarRegistroAutomatico)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Código do ESP32 já utilizado"));
        }
    }

    private DispositivoEsp32 validarRegistroAutomatico(DispositivoEsp32 dispositivo) {
        if (!dispositivo.isAtivo() || dispositivo.isExcluido()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ESP32 está desativado");
        }
        return dispositivo;
    }

    public DispositivoEsp32 selecionarAutomaticamente(boolean incluirOffline) {
        LocalDateTime limite = LocalDateTime.now().minusSeconds(30);
        List<DispositivoEsp32> ativos = listar().stream().filter(DispositivoEsp32::isAtivo).toList();
        List<DispositivoEsp32> online = ativos.stream()
                .filter(dispositivo -> repositorioDadosSensor
                        .findFirstByEsp32IdOrderByDataRegistroDesc(dispositivo.getId())
                        .map(DadosSensor::getDataRegistro)
                        .filter(ultima -> !ultima.isBefore(limite)).isPresent())
                .limit(2)
                .toList();
        if (online.isEmpty()) {
            if (incluirOffline && ativos.size() == 1) return ativos.getFirst();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aguardando leituras do ESP32");
        }
        if (online.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mais de um ESP32 online");
        }
        return online.getFirst();
    }

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
