package com.example.backend.support;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

// Réponses météo locales : aucun appel externe ni clé réelle pendant les tests automatisés.
public class OpenWeatherServeur implements AutoCloseable {
    private final HttpServer serveur;
    private final ExecutorService executeur = Executors.newVirtualThreadPerTaskExecutor();
    private final Clock horloge;
    public final AtomicInteger appels = new AtomicInteger();
    public volatile int statut = 200;
    public volatile long delaiMillis = 0;
    public volatile String actuelle;
    public volatile String previsions;
    public volatile String derniereRequete;

    public OpenWeatherServeur(Clock horloge) throws Exception {
        this.horloge = horloge;
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.setExecutor(executeur);
        serveur.createContext("/data/2.5/", echange -> {
            appels.incrementAndGet();
            derniereRequete = echange.getRequestURI().getRawQuery();
            try {
                if (delaiMillis > 0) Thread.sleep(delaiMillis);
                String texte;
                if (statut != 200) texte = "{\"message\":\"fournisseur en erreur\"}";
                else if (echange.getRequestURI().getPath().endsWith("/weather")) {
                    texte = actuelle == null ? actuelleValide() : actuelle;
                } else texte = previsions == null ? previsionsValides() : previsions;
                byte[] corps = texte.getBytes(StandardCharsets.UTF_8);
                echange.getResponseHeaders().set("Content-Type", "application/json");
                echange.sendResponseHeaders(statut, corps.length);
                echange.getResponseBody().write(corps);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally { echange.close(); }
        });
        serveur.start();
    }

    public String adresse() { return "http://127.0.0.1:" + serveur.getAddress().getPort() + "/data/2.5"; }

    public String actuelleValide() {
        return "{\"cod\":200,\"dt\":" + horloge.instant().getEpochSecond()
                + ",\"timezone\":3600,\"main\":{\"temp\":22.5,\"humidity\":60},"
                + "\"rain\":{\"1h\":0.5},\"weather\":[{\"description\":\"pluie légère\"}]}";
    }

    public String previsionsValides() {
        StringBuilder resultat = new StringBuilder("{\"cod\":\"200\",\"city\":{\"timezone\":3600},\"list\":[");
        for (int i = 1; i <= 40; i++) {
            if (i > 1) resultat.append(',');
            resultat.append("{\"dt\":").append(horloge.instant().plusSeconds(i * 10800L).getEpochSecond())
                    .append(",\"main\":{\"temp\":23,\"humidity\":65},\"pop\":0.7,")
                    .append("\"rain\":{\"3h\":1.2},\"weather\":[{\"description\":\"pluie\"}]}");
        }
        return resultat.append("]}").toString();
    }

    @Override public void close() { serveur.stop(0); executeur.close(); }
}
