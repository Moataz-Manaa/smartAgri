"""Préparer une configuration importable sans comptes personnels ni secrets.

Usage : python keycloak/prepare_export.py CHEMIN_EXPORT_BRUT
Le fichier brut reste dans son dossier temporaire.
"""
import json
import sys
from pathlib import Path

SECRET_VARIABLE = "${KEYCLOAK_ADMIN_CLIENT_SECRET}"
DESTINATION = Path(__file__).parent / "smartagri-realm.json"
SENSITIVE_FIELDS = {
    "secret", "password", "privatekey", "registrationaccesstoken",
    "clientsecret", "client-secret", "client_secret", "codesecret",
    "client.secret.rotated", "client.secret.creation.time",
    "client.secret.expiration.time", "client.secret.rotated.expiration.time",
}


def remove_secrets(value):
    if isinstance(value, dict):
        for field in list(value):
            if field.lower() in SENSITIVE_FIELDS:
                del value[field]
            else:
                remove_secrets(value[field])
    elif isinstance(value, list):
        for item in value:
            remove_secrets(item)


def prepare(source):
    realm = json.loads(source.read_text(encoding="utf-8-sig"))
    if realm.get("realm") != "smartagri":
        raise ValueError("Le fichier doit correspondre au realm smartagri.")
    if realm.get("identityProviders") or realm.get("userFederationProviders"):
        raise ValueError("Les fournisseurs externes nécessitent une vérification spécifique.")
    clients = {client["clientId"]: client for client in realm.get("clients", [])}
    for client_id in ("smartagri-admin", "smartagri-angular", "realm-management"):
        if client_id not in clients:
            raise ValueError("Un client requis est absent : " + client_id)

    # Les clés exportées sont propres au serveur d'origine.
    # Sans ces composants, Keycloak génère de nouvelles clés lors de l'import.
    components = realm.get("components", {})
    components.pop("org.keycloak.keys.KeyProvider", None)
    if "org.keycloak.storage.UserStorageProvider" in components:
        raise ValueError("Le stockage externe des utilisateurs nécessite une vérification spécifique.")
    realm.pop("users", None)
    realm.pop("federatedUsers", None)
    remove_secrets(realm)

    admin = clients["smartagri-admin"]
    admin["secret"] = SECRET_VARIABLE
    admin["serviceAccountsEnabled"] = True
    admin["publicClient"] = False
    # Ce client appelle l'API d'administration sans connexion par navigateur.
    admin["standardFlowEnabled"] = False
    admin["implicitFlowEnabled"] = False
    admin["directAccessGrantsEnabled"] = False
    admin["redirectUris"] = []
    admin["webOrigins"] = []

    # Uniquement le compte technique, sans compte personnel ni mot de passe.
    realm["users"] = [{
        "username": "service-account-smartagri-admin",
        "enabled": True,
        "serviceAccountClientId": "smartagri-admin",
        "clientRoles": {
            "realm-management": ["manage-users", "view-realm"]
        }
    }]
    DESTINATION.write_text(
        json.dumps(realm, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print("Configuration nettoyée : keycloak/smartagri-realm.json")
    print("Comptes personnels exclus ; clés privées retirées ; secret paramétré.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage : python keycloak/prepare_export.py CHEMIN_EXPORT_BRUT")
    try:
        prepare(Path(sys.argv[1]))
    except (ValueError, OSError):
        # Ne jamais imprimer le contenu de l'export brut dans les erreurs.
        raise SystemExit("Préparation impossible. Vérifier le chemin et la structure de l'export.")
