"""Teste REST com MongoDB temporário. Requer JAR compilado, mongod e mongosh."""
import json
import os
import secrets
import socket
import subprocess
import tempfile
import time
from contextlib import ExitStack
from datetime import datetime, timedelta
from pathlib import Path
from urllib import error, request
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[3]
JAR = ROOT / "target/smartberry-0.0.1-SNAPSHOT.jar"
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"
FUSO = ZoneInfo("America/Sao_Paulo")
CHAVE = secrets.token_hex(24)


def porta_local():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def encerrar(processo):
    if processo.poll() is None:
        processo.terminate()
        processo.wait(timeout=20)


def api(porta, caminho, esperado=200, metodo="GET", dados=None):
    corpo = None if dados is None else json.dumps(dados).encode()
    chamada = request.Request(f"http://127.0.0.1:{porta}/api{caminho}", data=corpo,
                              method=metodo, headers={"X-SmartBerry-Key": CHAVE,
                                                       "Content-Type": "application/json"})
    try:
        with request.urlopen(chamada, timeout=5) as resposta:
            assert resposta.status == esperado, (caminho, resposta.status, esperado)
            return json.load(resposta) if esperado != 204 else None
    except error.HTTPError as falha:
        assert falha.code == esperado, (caminho, falha.code, esperado, falha.read())


def horario(segundos):
    return (datetime.now(FUSO) + timedelta(seconds=segundos)).replace(tzinfo=None).isoformat()


def iniciar_api(pilha, pasta, porta, mongo_uri):
    ambiente = dict(os.environ, SMARTBERRY_API_KEY=CHAVE, MONGODB_URI=mongo_uri, SERVER_PORT=str(porta))
    arquivo = pilha.enter_context(open(pasta / f"api-{porta}-{time.time_ns()}.log", "w+"))
    processo = subprocess.Popen([JAVA, "-jar", str(JAR), "--server.address=127.0.0.1"],
                                env=ambiente, stdout=arquivo, stderr=subprocess.STDOUT)
    pilha.callback(encerrar, processo)
    for _ in range(60):
        if processo.poll() is not None:
            break
        try:
            api(porta, "/esp32")
            return processo
        except error.URLError:
            time.sleep(.25)
    arquivo.seek(0)
    raise RuntimeError("A API não iniciou:\n" + arquivo.read()[-3500:])


def esperar_comando(porta, id_comando):
    inicio = time.monotonic()
    while time.monotonic() - inicio < 20:
        try:
            return api(porta, "/comandos/" + id_comando)
        except AssertionError as erro:
            if erro.args[0][1] != 404:
                raise
        time.sleep(.3)
    raise AssertionError("Comando esperado não foi enfileirado")


def testar():
    assert JAR.is_file(), "Empacote a API com o Maven antes de executar o teste"
    porta_mongo, porta, porta2 = porta_local(), porta_local(), porta_local()
    mongo_uri = f"mongodb://127.0.0.1:{porta_mongo}/irrigacao_teste"
    with tempfile.TemporaryDirectory(prefix="smartberry-irrigacao-") as temporario, ExitStack() as pilha:
        pasta = Path(temporario)
        dados_mongo = pasta / "mongo"
        dados_mongo.mkdir()
        log_mongo = pilha.enter_context(open(pasta / "mongo.log", "w"))
        mongo = subprocess.Popen(["mongod", "--port", str(porta_mongo), "--dbpath", str(dados_mongo),
                                  "--bind_ip", "127.0.0.1", "--nounixsocket"],
                                 stdout=log_mongo, stderr=subprocess.STDOUT)
        pilha.callback(encerrar, mongo)

        def consultar_mongo(script):
            return subprocess.check_output(["mongosh", mongo_uri, "--quiet", "--eval", script],
                                           text=True, timeout=12).strip()

        instancia = iniciar_api(pilha, pasta, porta, mongo_uri)
        esp = api(porta, "/esp32/registro/esp32-123456789abc", 200, "PUT")["id"]
        assert api(porta, "/esp32/automatico?incluirOffline=true")["id"] == esp
        api(porta, "/esp32/automatico", 404)
        path = f"/esp32/{esp}/agendamentos-irrigacao"
        fila = f"/comandos/esp32/{esp}/proximo"
        api(porta, path, 400, "POST", {"horario": horario(-30), "duracaoSegundos": 60})
        api(porta, path, 400, "POST", {"horario": "data-inválida", "duracaoSegundos": 60})
        api(porta, path, 400, "POST", {"horario": horario(60), "duracaoSegundos": 301})

        cancelado = api(porta, path, 201, "POST", {"horario": horario(3), "duracaoSegundos": 60})
        api(porta, path + "/" + cancelado["id"], 204, "DELETE")
        api(porta, path + "/" + cancelado["id"], 204, "DELETE")
        agendado = api(porta, path, 201, "POST", {"horario": horario(13), "duracaoSegundos": 60})
        id_comando = "agendamento-" + agendado["id"]
        api(porta, "/comandos/" + id_comando, 404)
        encerrar(instancia)
        # Marca a janela de recuperação entre a transição de estado e a inserção do comando.
        assert consultar_mongo(
            f'print(db.agendamentos_irrigacao.updateOne({{_id:"{agendado["id"]}"}},'
            '{$set:{estado:"DISPARANDO"}}).modifiedCount)'
        ) == "1"
        iniciar_api(pilha, pasta, porta, mongo_uri)
        iniciar_api(pilha, pasta, porta2, mongo_uri)
        estados = {item["id"]: item["status"] for item in api(porta, path)}
        assert estados[cancelado["id"]] == "CANCELADO"
        assert estados[agendado["id"]] in ("DISPARANDO", "AGUARDANDO_PLACA")
        comando = esperar_comando(porta, id_comando)
        assert datetime.now(FUSO).replace(tzinfo=None) >= datetime.fromisoformat(agendado["horario"])
        assert comando["tipo"] == "ACIONAR_IRRIGACAO" and comando["valor"] == 60
        assert consultar_mongo(f'print(db.comandos_esp32.countDocuments({{_id:"{id_comando}"}}))') == "1"
        api(porta, "/comandos/agendamento-" + cancelado["id"], 404)
        assert next(a for a in api(porta, path) if a["id"] == agendado["id"])["status"] == "AGUARDANDO_PLACA"
        api(porta, path + "/" + agendado["id"], 409, "DELETE")
        assert api(porta2, fila)["id"] == id_comando
        api(porta, f"/comandos/{id_comando}/executado", 200, "PATCH")
        assert next(a for a in api(porta, path) if a["id"] == agendado["id"])["status"] == "EXECUTADO"
        api(porta, fila, 204)
        # Reprocessamento após um ACK preserva a mesma identidade e o estado do comando.
        assert consultar_mongo(
            f'print(db.agendamentos_irrigacao.updateOne({{_id:"{agendado["id"]}"}},'
            '{$set:{estado:"DISPARANDO"}}).modifiedCount)'
        ) == "1"
        time.sleep(2)
        assert api(porta, "/comandos/" + id_comando)["status"] == "EXECUTADO"
        assert consultar_mongo(f'print(db.comandos_esp32.countDocuments({{_id:"{id_comando}"}}))') == "1"
        print("Criação, persistência, cancelamento, disparo no horário, ACK e recuperação após reinício.", flush=True)

        disputa = api(porta, path, 201, "POST", {"horario": horario(2), "duracaoSegundos": 60})
        comando_disputa = "agendamento-" + disputa["id"]
        esperar_comando(porta, comando_disputa)
        parada = api(porta, "/comandos", 201, "POST", {"esp32Id": esp, "tipo": "PARAR_IRRIGACAO"})
        assert api(porta, fila)["id"] == parada["id"]
        assert api(porta, "/comandos/" + comando_disputa)["status"] == "REJEITADO"
        api(porta, f'/comandos/{parada["id"]}/executado', 200, "PATCH")
        api(porta, "/comandos", 400, "POST",
            {"esp32Id": esp, "tipo": "CONFIGURAR_IRRIGACAO_AUTOMATICA", "valor": 2})
        for modo in (1, 0):
            alteracao = api(porta, "/comandos", 201, "POST",
                            {"esp32Id": esp, "tipo": "CONFIGURAR_IRRIGACAO_AUTOMATICA", "valor": modo})
            proximo = api(porta2, fila)
            assert (proximo["id"], proximo["tipo"], proximo["valor"]) == (
                alteracao["id"], "CONFIGURAR_IRRIGACAO_AUTOMATICA", modo)
            api(porta, f'/comandos/{alteracao["id"]}/executado', 200, "PATCH")
        print("Prioridade da parada manual e contratos de ativação/desativação validados.", flush=True)

        ventilacao = f"/esp32/{esp}/agendamentos-ventilacao"
        assert api(porta, ventilacao + "/automatico") == {"ativo": False}
        diario = api(porta, ventilacao, 201, "POST",
                     {"horario": horario(80), "duracaoSegundos": 60, "diario": True})
        assert api(porta, ventilacao)[0]["status"] == "PAUSADO"
        assert api(porta, path + "/automatico") == {"ativo": False}
        api(porta, ventilacao + "/automatico", 200, "PUT", {"ativo": True})
        assert api(porta, ventilacao)[0]["status"] == "PENDENTE"
        editado = api(porta, ventilacao + "/" + diario["id"], 200, "PUT",
                      {"horario": horario(5), "duracaoSegundos": 120, "diario": True})
        espera = time.monotonic() + 12
        while time.monotonic() < espera:
            resposta = api(porta, ventilacao)[0]
            if resposta["status"] == "AGUARDANDO_PLACA":
                break
            time.sleep(.4)
        else:
            raise AssertionError("Ventilação diária não enfileirada")
        comando_ventilacao = api(porta2, fila)
        assert comando_ventilacao["tipo"] == "ACIONAR_VENTILACAO_TEMPORIZADA"
        assert comando_ventilacao["valor"] == 120 and comando_ventilacao["agendamentoDiario"]
        api(porta, f'/comandos/{comando_ventilacao["id"]}/executado', 200, "PATCH")
        antigo = int((datetime.now(FUSO) - timedelta(minutes=1)).timestamp() * 1000)
        assert consultar_mongo(
            f'print(db.agendamentos_irrigacao.updateOne({{_id:"{editado["id"]}"}},'
            f'{{$set:{{programadoPara:new Date({antigo})}}}}).modifiedCount)'
        ) == "1"
        time.sleep(2)
        assert api(porta, ventilacao)[0]["status"] == "PENDENTE"
        api(porta, ventilacao + "/automatico", 200, "PUT", {"ativo": False})
        assert api(porta, ventilacao)[0]["status"] == "PAUSADO"
        api(porta, ventilacao + "/" + diario["id"], 204, "DELETE")
        assert api(porta, ventilacao)[0]["status"] == "CANCELADO"
        print("Ventilação diária: criação, edição, fila temporizada, recorrência, pausa e exclusão.", flush=True)

        expirado = api(porta, path, 201, "POST", {"horario": horario(10), "duracaoSegundos": 60})
        antigo = int((datetime.now(FUSO) - timedelta(seconds=35)).timestamp() * 1000)
        assert consultar_mongo(
            f'print(db.agendamentos_irrigacao.updateOne({{_id:"{expirado["id"]}"}},'
            f'{{$set:{{programadoPara:new Date({antigo})}}}}).modifiedCount)'
        ) == "1"
        time.sleep(2)
        assert next(a for a in api(porta, path) if a["id"] == expirado["id"])["status"] == "EXPIRADO"
        api(porta, "/comandos/agendamento-" + expirado["id"], 404)
        print("Agendamento vencido sem reativação da bomba após a janela de 30 segundos.", flush=True)


if __name__ == "__main__":
    testar()
