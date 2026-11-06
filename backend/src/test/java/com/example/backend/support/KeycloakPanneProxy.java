package com.example.backend.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Relais de test : transmettre à Keycloak, sauf pour les pannes demandées. */
public class KeycloakPanneProxy implements AutoCloseable {
    private final HttpServer serveur;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final String destination;
    private final List<Panne> pannes = new ArrayList<>();
    private int pannesDeclenchees;

    public KeycloakPanneProxy(String destination) throws IOException {
        this.destination = destination;
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", this::transmettre);
        serveur.start();
    }

    public String adresse() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    public synchronized void echouer(String methode, String chemin, String contenu, int statut) {
        pannes.add(new Panne(methode, chemin, contenu, statut));
    }

    public synchronized void reinitialiser() {
        pannes.clear();
        pannesDeclenchees = 0;
    }

    public synchronized int nombrePannes() {
        return pannesDeclenchees;
    }

    private synchronized int panneDemandee(String methode, String chemin, String contenu) {
        for (int i = 0; i < pannes.size(); i++) {
            Panne panne = pannes.get(i);
            if (panne.methode.equals(methode) && chemin.contains(panne.chemin)
                    && contenu.replaceAll("\\s", "").contains(panne.contenu)) {
                pannes.remove(i);
                pannesDeclenchees++;
                return panne.statut;
            }
        }
        return 0;
    }

    private void transmettre(HttpExchange echange) throws IOException {
        try {
            byte[] corps = echange.getRequestBody().readAllBytes();
            int statut = panneDemandee(echange.getRequestMethod(), echange.getRequestURI().getPath(),
                    new String(corps, java.nio.charset.StandardCharsets.UTF_8));
            if (statut != 0) {
                echange.sendResponseHeaders(statut, -1);
                return;
            }
            HttpRequest.Builder requete = HttpRequest.newBuilder(
                    URI.create(destination + echange.getRequestURI())).timeout(Duration.ofSeconds(20))
                    .method(echange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(corps));
            for (String nom : new String[]{"Authorization", "Content-Type", "Accept"}) {
                String valeur = echange.getRequestHeaders().getFirst(nom);
                if (valeur != null) requete.header(nom, valeur);
            }
            HttpResponse<byte[]> reponse = client.send(requete.build(), HttpResponse.BodyHandlers.ofByteArray());
            for (String nom : new String[]{"Content-Type", "Location"}) {
                reponse.headers().firstValue(nom).ifPresent(v -> echange.getResponseHeaders().set(nom, v));
            }
            if (reponse.body().length == 0) {
                echange.sendResponseHeaders(reponse.statusCode(), -1);
            } else {
                echange.sendResponseHeaders(reponse.statusCode(), reponse.body().length);
                echange.getResponseBody().write(reponse.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            echange.sendResponseHeaders(502, -1);
        } finally {
            echange.close();
        }
    }

    @Override
    public void close() {
        serveur.stop(0);
        client.close();
    }

    private static class Panne {
        final String methode;
        final String chemin;
        final String contenu;
        final int statut;

        Panne(String methode, String chemin, String contenu, int statut) {
            this.methode = methode;
            this.chemin = chemin;
            this.contenu = contenu;
            this.statut = statut;
        }
    }
}
