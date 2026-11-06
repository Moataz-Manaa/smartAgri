"""Tester l'import dans un conteneur neuf, puis supprimer ce conteneur.

Usage : python keycloak/verify_import.py
Aucun volume du serveur existant n'est utilisé.
"""
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from prepare_export import SECRET_VARIABLE, SENSITIVE_FIELDS

IMAGE = "quay.io/keycloak/keycloak:26.8.1"
FOLDER = Path(__file__).resolve().parent
CONTAINER = "smartagri-keycloak-import-test-" + uuid.uuid4().hex[:10]


def docker(*arguments, environment=None):
    result = subprocess.run(
        ["docker", *arguments], env=environment, capture_output=True, text=True,
        timeout=120,
    )
    if result.returncode:
        # Aucun inspect global, log complet ou secret dans les diagnostics.
        raise RuntimeError("Une commande Docker a échoué : " + arguments[0])
    return result.stdout.strip()


def request_json(url, token=None, form=None, method=None, payload=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if form is not None:
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        data = urllib.parse.urlencode(form).encode()
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    # Les requêtes sont locales ; ne pas utiliser un proxy configuré sur la machine.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=5) as response:
        body = response.read()
        return json.loads(body) if body else None


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def check_no_secrets(value):
    if isinstance(value, dict):
        for field, item in value.items():
            if field.lower() in SENSITIVE_FIELDS:
                check(field == "secret" and item == SECRET_VARIABLE,
                      "Un champ sensible non paramétré est présent.")
            check_no_secrets(item)
    elif isinstance(value, list):
        for item in value:
            check_no_secrets(item)


def main():
    realm = json.loads((FOLDER / "smartagri-realm.json").read_text(encoding="utf-8"))
    check_no_secrets(realm)
    check(realm["realm"] == "smartagri", "Realm incorrect.")
    check(len(realm["users"]) == 1, "Des comptes inattendus sont présents.")
    check(realm["users"][0].get("serviceAccountClientId") == "smartagri-admin",
          "Compte technique absent.")
    check("org.keycloak.keys.KeyProvider" not in realm.get("components", {}),
          "Les anciennes clés sont encore présentes.")
    admin = next(c for c in realm["clients"] if c["clientId"] == "smartagri-admin")
    check(admin["secret"] == "${KEYCLOAK_ADMIN_CLIENT_SECRET}", "Secret non paramétré.")
    check(not realm.get("identityProviders"), "Fournisseur externe à vérifier.")

    environment = os.environ.copy()
    # Identifiants aléatoires limités à ce test, jamais affichés ni sauvegardés.
    environment["KEYCLOAK_ADMIN_CLIENT_SECRET"] = secrets.token_urlsafe(32)
    environment["KC_BOOTSTRAP_ADMIN_USERNAME"] = "import-test-admin"
    environment["KC_BOOTSTRAP_ADMIN_PASSWORD"] = secrets.token_urlsafe(32)
    started = False
    try:
        docker("run", "-d", "--name", CONTAINER,
               "-p", "127.0.0.1::8080",
               "--env", "KEYCLOAK_ADMIN_CLIENT_SECRET",
               "--env", "KC_BOOTSTRAP_ADMIN_USERNAME",
               "--env", "KC_BOOTSTRAP_ADMIN_PASSWORD",
               "--mount", "type=bind,source=" + str(FOLDER) + ",target=/opt/keycloak/data/import,readonly",
               IMAGE, "start-dev", "--import-realm", environment=environment)
        started = True
        ports = json.loads(docker("inspect", CONTAINER, "--format", "{{json .NetworkSettings.Ports}}"))
        port = ports["8080/tcp"][0]["HostPort"]
        base = "http://127.0.0.1:" + port
        endpoint = base + "/realms/smartagri/protocol/openid-connect/token"
        print("Conteneur de test créé ; attente de l'import...", flush=True)
        deadline = time.monotonic() + 120
        while True:
            try:
                discovery = request_json(base + "/realms/smartagri/.well-known/openid-configuration")
                break
            except (OSError, ValueError):
                if time.monotonic() > deadline:
                    raise RuntimeError("Le realm n'est pas disponible après 120 secondes.")
                time.sleep(2)
        check(discovery["issuer"] == base + "/realms/smartagri", "Émetteur incorrect.")
        keys = request_json(discovery["jwks_uri"])
        check(bool(keys.get("keys")), "Aucune clé de signature générée.")
        print("OK : realm importé et configuration OpenID disponible.", flush=True)

        token = request_json(endpoint, form={
            "grant_type": "client_credentials",
            "client_id": "smartagri-admin",
            "client_secret": environment["KEYCLOAK_ADMIN_CLIENT_SECRET"],
        })["access_token"]
        print("OK : le secret fourni par variable permet l'authentification du compte technique.", flush=True)
        prefix = base + "/admin/realms/smartagri"
        roles = request_json(prefix + "/roles", token=token)
        check({"ADMIN", "AGRICULTEUR", "AGRONOME"}.issubset({r["name"] for r in roles}),
              "Rôles métier manquants.")
        users = request_json(prefix + "/users", token=token)
        # La recherche d'utilisateurs peut exclure les comptes de service.
        check(all(user.get("username") == "service-account-smartagri-admin" for user in users),
              "Des comptes personnels ont été importés.")
        # Authentification bootstrap uniquement sur cette nouvelle instance.
        bootstrap = request_json(base + "/realms/master/protocol/openid-connect/token", form={
            "grant_type": "password", "client_id": "admin-cli",
            "username": environment["KC_BOOTSTRAP_ADMIN_USERNAME"],
            "password": environment["KC_BOOTSTRAP_ADMIN_PASSWORD"],
        })["access_token"]
        clients = request_json(prefix + "/clients", token=bootstrap)
        service_account = next(c for c in clients if c["clientId"] == "smartagri-admin")
        realm_management = next(c for c in clients if c["clientId"] == "realm-management")
        account = request_json(prefix + "/clients/" + service_account["id"] + "/service-account-user", token=bootstrap)
        check(account.get("username") == "service-account-smartagri-admin"
              and account.get("enabled") is True, "Compte technique incorrect.")
        mappings = request_json(prefix + "/users/" + account["id"] +
                                "/role-mappings/clients/" + realm_management["id"], token=bootstrap)
        check({"manage-users", "view-realm"}.issubset({r["name"] for r in mappings}),
              "Permissions du compte technique manquantes.")
        angular = next(c for c in clients if c["clientId"] == "smartagri-angular")
        check(angular["publicClient"] is True, "Angular doit rester un client public.")
        check("http://localhost:4200/*" in angular.get("redirectUris", []), "Redirection Angular absente.")
        check("http://localhost:4200" in angular.get("webOrigins", []), "Origine Angular absente.")
        check(angular.get("attributes", {}).get("pkce.code.challenge.method") == "S256", "PKCE S256 absent.")
        mappers = request_json(prefix + "/clients/" + angular["id"] + "/protocol-mappers/models", token=bootstrap)
        check(any(m.get("config", {}).get("included.custom.audience") == "smartagri-api"
                  or m.get("config", {}).get("included.client.audience") == "smartagri-api"
                  for m in mappers), "Audience smartagri-api absente.")
        claims_part = token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(claims_part + "=" * (-len(claims_part) % 4)))
        check({"manage-users", "view-realm"}.issubset(
            set(claims.get("resource_access", {}).get("realm-management", {}).get("roles", []))),
            "Les permissions ne sont pas incluses dans le token.")
        # Vérifier manage-users avec une opération réelle, uniquement dans ce conteneur jetable.
        username = "verification-import-" + uuid.uuid4().hex[:10]
        request_json(prefix + "/users", token=token, method="POST",
                     payload={"username": username, "enabled": False})
        created = request_json(prefix + "/users?username=" + username + "&exact=true", token=token)
        check(len(created) == 1, "La création d'un utilisateur de test a échoué.")
        user_url = prefix + "/users/" + created[0]["id"]
        request_json(user_url, token=token, method="PUT", payload={"lastName": "Verification"})
        check(request_json(user_url, token=token).get("lastName") == "Verification",
              "La modification d'un utilisateur de test a échoué.")
        request_json(user_url, token=token, method="DELETE")
        print("OK : création, modification et suppression d'un utilisateur de test autorisées.", flush=True)
        print("OK : rôles métier, permissions du compte technique, Angular, PKCE et audience conservés.", flush=True)
        print("OK : aucun compte personnel importé ; nouvelles clés générées par Keycloak.", flush=True)
        print("Test de réimportation réussi.", flush=True)
    finally:
        if started:
            docker("rm", "-f", "-v", CONTAINER)
            print("Conteneur temporaire et stockage de test supprimés.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        raise SystemExit("Test échoué : HTTP " + str(error.code) + " sur " + urllib.parse.urlsplit(error.url).path)
    except RuntimeError as error:
        raise SystemExit("Test échoué : " + str(error))
    except (OSError, ValueError, KeyError, StopIteration, subprocess.TimeoutExpired):
        raise SystemExit("Test échoué. Vérifier Docker, la configuration du realm et les derniers contrôles affichés.")
