"""Recette backend avec Keycloak et PostgreSQL réels, dans deux conteneurs jetables.

Usage depuis la racine : python keycloak/verify_backend.py
Le rapport Maven est conservé dans backend/target/recette-reelle.log.
"""
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import time
import urllib.error
import uuid
from verify_import import docker, request_json, IMAGE

ROOT = Path(__file__).resolve().parent.parent
IMPORT = ROOT / "keycloak"
PREFIX = "smartagri-recette-" + uuid.uuid4().hex[:10]


def port(container, internal):
    ports = json.loads(docker("inspect", container, "--format", "{{json .NetworkSettings.Ports}}"))
    return ports[str(internal) + "/tcp"][0]["HostPort"]


def main():
    maven = shutil.which("mvn.cmd") or shutil.which("mvn")
    if not maven:
        raise RuntimeError("Maven doit être installé pour lancer la recette.")
    environment = os.environ.copy()
    environment.update({
        "SMARTAGRI_REAL_TESTS": "true",
        "KEYCLOAK_ADMIN_CLIENT_SECRET": secrets.token_urlsafe(32),
        "KC_BOOTSTRAP_ADMIN_USERNAME": "import-test-admin",
        "KC_BOOTSTRAP_ADMIN_PASSWORD": secrets.token_urlsafe(32),
        "POSTGRES_USER": "smartagri_recette",
        "POSTGRES_DB": "smartagri_recette",
        "POSTGRES_PASSWORD": secrets.token_urlsafe(32),
    })
    environment["SMARTAGRI_TEST_DATABASE_PASSWORD"] = environment["POSTGRES_PASSWORD"]
    containers = []
    try:
        database = PREFIX + "-postgres"
        docker("run", "-d", "--name", database, "-p", "127.0.0.1::5432",
               "--env", "POSTGRES_USER", "--env", "POSTGRES_DB", "--env", "POSTGRES_PASSWORD",
               "postgres:18-alpine", environment=environment)
        containers.append(database)
        environment["SMARTAGRI_TEST_DATABASE_URL"] = (
            "jdbc:postgresql://127.0.0.1:" + port(database, 5432) + "/smartagri_recette")
        print("PostgreSQL temporaire démarré.", flush=True)

        keycloak = PREFIX + "-keycloak"
        docker("run", "-d", "--name", keycloak, "-p", "127.0.0.1::8080",
               "--env", "KEYCLOAK_ADMIN_CLIENT_SECRET", "--env", "KC_BOOTSTRAP_ADMIN_USERNAME",
               "--env", "KC_BOOTSTRAP_ADMIN_PASSWORD",
               "--mount", "type=bind,source=" + str(IMPORT) + ",target=/opt/keycloak/data/import,readonly",
               IMAGE, "start-dev", "--import-realm", environment=environment)
        containers.append(keycloak)
        base = "http://127.0.0.1:" + port(keycloak, 8080)
        environment["SMARTAGRI_TEST_KEYCLOAK_URL"] = base
        print("Keycloak temporaire démarré ; attente de l'import...", flush=True)
        deadline = time.monotonic() + 150
        while True:
            try:
                request_json(base + "/realms/smartagri/.well-known/openid-configuration")
                break
            except (OSError, ValueError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("Le realm temporaire n'est pas disponible.")
                time.sleep(2)
        docker("exec", database, "pg_isready", "-U", "smartagri_recette", "-d", "smartagri_recette")
        print("Services prêts ; exécution des tests backend réels.", flush=True)
        arguments = [maven, "-o", "-B", "-Dtest=RecetteKeycloakTests", "-Dlogging.level.root=ERROR"]
        repository = Path(environment.get("SMARTAGRI_MAVEN_REPOSITORY", str(Path.home() / ".m2" / "repository")))
        if repository.is_dir():
            arguments.append("-Dmaven.repo.local=" + str(repository))
        arguments.append("test")
        report = ROOT / "backend" / "target" / "recette-reelle.log"
        report.parent.mkdir(exist_ok=True)
        with report.open("w", encoding="utf-8") as output:
            result = subprocess.run(arguments, cwd=ROOT / "backend", env=environment,
                                    stdout=output, stderr=subprocess.STDOUT, timeout=300)
        if result.returncode:
            raise RuntimeError("La recette a échoué. Consulter backend/target/recette-reelle.log.")
        print("Recette backend réussie. Rapport : backend/target/recette-reelle.log", flush=True)
    finally:
        for container in reversed(containers):
            docker("rm", "-f", "-v", container)
        if containers:
            print("Conteneurs et stockage temporaire supprimés.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        # Aucun jeton, corps de requête ou identifiant confidentiel n'est imprimé.
        raise SystemExit(str(error))
