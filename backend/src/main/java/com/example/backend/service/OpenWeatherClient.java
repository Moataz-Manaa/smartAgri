package com.example.backend.service;

import com.example.backend.dto.meteo.ConditionsMeteoResponse;
import com.example.backend.dto.meteo.DonneesMeteoResponse;
import com.example.backend.dto.meteo.OpenWeatherResponse;
import com.example.backend.dto.meteo.PrevisionMeteoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenWeatherClient {
    private final RestClient meteoRestClient;
    private final Clock meteoHorloge;
    @Value("${app.meteo.api-key:}")
    private String apiKey = "";

    public DonneesMeteoResponse consulter(double latitude, double longitude) {
        if (apiKey.isBlank()) {
            throw indisponible("La clé du service météo n'est pas configurée");
        }
        try {
            OpenWeatherResponse actuelle = appeler("/weather", latitude, longitude);
            OpenWeatherResponse previsions = appeler("/forecast", latitude, longitude);
            return convertir(actuelle, previsions);
        } catch (RestClientResponseException exception) {
            int statut = exception.getStatusCode().value();
            if (statut == 401 || statut == 403) {
                throw indisponible("La clé météo est invalide ou n'est pas encore activée");
            }
            if (statut == 429) {
                throw indisponible("Le quota du service météo est temporairement atteint");
            }
            throw indisponible("Le service météo est temporairement indisponible");
        } catch (RestClientException exception) {
            // Ne pas exposer l'URL de la requête : elle contient la clé API.
            throw indisponible("Impossible de joindre le service météo dans le délai prévu");
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw indisponible("Les données reçues du service météo sont invalides");
        }
    }

    private OpenWeatherResponse appeler(String chemin, double latitude, double longitude) {
        return meteoRestClient.get().uri(uri -> uri.path(chemin)
                .queryParam("lat", latitude).queryParam("lon", longitude)
                .queryParam("appid", apiKey).queryParam("units", "metric").queryParam("lang", "fr")
                .build()).retrieve().body(OpenWeatherResponse.class);
    }

    private DonneesMeteoResponse convertir(OpenWeatherResponse actuelle, OpenWeatherResponse previsions) {
        Instant maintenant = meteoHorloge.instant();
        verifierMesures(actuelle);
        if (!"200".equals(actuelle.getCod()) || previsions == null || !"200".equals(previsions.getCod())
                || actuelle.getTimezone() == null || Math.abs((long) actuelle.getTimezone()) > 18 * 3600
                || previsions.getCity() == null
                || !actuelle.getTimezone().equals(previsions.getCity().getTimezone())
                || previsions.getList() == null || previsions.getList().isEmpty() || previsions.getList().size() > 40) {
            throw indisponible("Les données reçues du service météo sont incomplètes");
        }
        Instant dateDonnees = Instant.ofEpochSecond(actuelle.getDt());
        if (dateDonnees.isBefore(maintenant.minusSeconds(3 * 3600))
                || dateDonnees.isAfter(maintenant.plusSeconds(600))) {
            throw indisponible("Le service météo fournit des données trop anciennes ou incohérentes");
        }
        ConditionsMeteoResponse conditions = ConditionsMeteoResponse.builder().dateDonnees(dateDonnees)
                .temperatureCelsius(actuelle.getMain().getTemp()).humiditePourcent(actuelle.getMain().getHumidity())
                .pluieDerniereHeureMillimetres(pluie(actuelle, "1h")).description(description(actuelle)).build();
        List<PrevisionMeteoResponse> liste = new ArrayList<>();
        Instant precedente = null;
        for (OpenWeatherResponse element : previsions.getList()) {
            verifierMesures(element);
            Instant date = Instant.ofEpochSecond(element.getDt());
            if ((precedente != null && !date.isAfter(precedente))
                    || date.isAfter(maintenant.plusSeconds(6 * 24 * 3600))) {
                throw indisponible("Les dates des prévisions météo sont incohérentes");
            }
            precedente = date;
            if (!date.isAfter(maintenant)) continue;
            Double probabilite = element.getPop();
            verifierNombre(probabilite, 0, 1);
            liste.add(PrevisionMeteoResponse.builder().datePrevision(date)
                    .temperatureCelsius(element.getMain().getTemp()).humiditePourcent(element.getMain().getHumidity())
                    .probabilitePrecipitationsPourcent(probabilite == null ? null : probabilite * 100)
                    .pluieTroisHeuresMillimetres(pluie(element, "3h")).description(description(element)).build());
        }
        if (liste.isEmpty()) throw indisponible("Aucune prévision météo à venir n'est disponible");
        return DonneesMeteoResponse.builder().recupereesLe(maintenant)
                .decalageUtcSecondes(actuelle.getTimezone()).conditions(conditions).previsions(List.copyOf(liste)).build();
    }

    private void verifierMesures(OpenWeatherResponse donnees) {
        if (donnees == null || donnees.getDt() == null || donnees.getMain() == null) {
            throw indisponible("Les données reçues du service météo sont incomplètes");
        }
        verifierNombre(donnees.getMain().getTemp(), -150, 100);
        verifierNombre(donnees.getMain().getHumidity(), 0, 100);
    }

    private void verifierNombre(Double valeur, double minimum, double maximum) {
        if (valeur != null && (!Double.isFinite(valeur) || valeur < minimum || valeur > maximum)) {
            throw indisponible("Une mesure reçue du service météo est invalide");
        }
    }

    private Double pluie(OpenWeatherResponse donnees, String periode) {
        Double valeur = donnees.getRain() == null ? null : donnees.getRain().get(periode);
        verifierNombre(valeur, 0, Double.MAX_VALUE);
        return valeur;
    }

    private String description(OpenWeatherResponse donnees) {
        if (donnees.getWeather() == null || donnees.getWeather().isEmpty() || donnees.getWeather().getFirst() == null) return null;
        return donnees.getWeather().getFirst().getDescription();
    }

    private ResponseStatusException indisponible(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
