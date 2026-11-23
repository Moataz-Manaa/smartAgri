package com.example.backend;

import com.example.backend.config.MeteoConfig;
import com.example.backend.service.OpenWeatherClient;
import com.example.backend.support.HorlogeMeteoTest;
import com.example.backend.support.OpenWeatherServeur;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class OpenWeatherClientTests {
    OpenWeatherServeur serveur;
    OpenWeatherClient client;
    HorlogeMeteoTest horloge;

    @BeforeEach void preparer() throws Exception {
        horloge = new HorlogeMeteoTest();
        serveur = new OpenWeatherServeur(horloge);
        client = new OpenWeatherClient(new MeteoConfig().meteoRestClient(serveur.adresse(), 1, 1), horloge);
        ReflectionTestUtils.setField(client, "apiKey", "cle-fictive-test");
    }

    @AfterEach void fermer() { serveur.close(); }

    @Test void convertirLesUnitesDatesEtProbabilites() {
        var donnees = client.consulter(34.74, 10.76);
        assertThat(donnees.getConditions().getTemperatureCelsius()).isEqualTo(22.5);
        assertThat(donnees.getConditions().getHumiditePourcent()).isEqualTo(60);
        assertThat(donnees.getConditions().getPluieDerniereHeureMillimetres()).isEqualTo(0.5);
        assertThat(donnees.getConditions().getDateDonnees()).isEqualTo(horloge.instant());
        assertThat(donnees.getDecalageUtcSecondes()).isEqualTo(3600);
        assertThat(donnees.getPrevisions()).hasSize(40);
        assertThat(donnees.getPrevisions().getFirst().getProbabilitePrecipitationsPourcent()).isEqualTo(70);
        assertThat(donnees.getPrevisions().getFirst().getPluieTroisHeuresMillimetres()).isEqualTo(1.2);
        assertThat(serveur.appels.get()).isEqualTo(2);
        assertThat(serveur.derniereRequete).contains("lat=34.74", "lon=10.76", "units=metric", "lang=fr");
    }

    @Test void conserverLesMesuresAbsentesSansInventerDeZero() {
        serveur.actuelle = serveur.actuelleValide().replace("\"rain\":{\"1h\":0.5},", "");
        serveur.previsions = serveur.previsionsValides().replace("\"rain\":{\"3h\":1.2},", "").replace("\"pop\":0.7,", "");
        var donnees = client.consulter(0, 0);
        assertThat(donnees.getConditions().getPluieDerniereHeureMillimetres()).isNull();
        assertThat(donnees.getPrevisions().getFirst().getPluieTroisHeuresMillimetres()).isNull();
        assertThat(donnees.getPrevisions().getFirst().getProbabilitePrecipitationsPourcent()).isNull();
    }

    @Test void refuserLesReponsesIncompletesOuMalFormees() {
        for (String corps : new String[]{"{}", "null", "ceci n'est pas du JSON"}) {
            serveur.actuelle = corps;
            assertThatThrownBy(() -> client.consulter(0, 0)).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test void refuserLaProbabiliteHorsLimites() {
        serveur.previsions = serveur.previsionsValides().replace("\"pop\":0.7", "\"pop\":2");
        assertThatThrownBy(() -> client.consulter(0, 0)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void refuserUneHumiditeHorsLimites() {
        serveur.actuelle = serveur.actuelleValide().replace("\"humidity\":60", "\"humidity\":101");
        assertThatThrownBy(() -> client.consulter(0, 0)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void refuserLesDonneesTropAnciennes() {
        serveur.actuelle = serveur.actuelleValide();
        horloge.avancer(4 * 3600);
        assertThatThrownBy(() -> client.consulter(0, 0)).hasMessageContaining("anciennes");
    }

    @Test void refuserUneDateActuelleDansLeFutur() {
        serveur.actuelle = serveur.actuelleValide().replace("" + horloge.instant().getEpochSecond(),
                "" + horloge.instant().plusSeconds(7200).getEpochSecond());
        assertThatThrownBy(() -> client.consulter(0, 0)).hasMessageContaining("incohérentes");
    }

    @Test void refuserLesPrevisionsVides() {
        serveur.previsions = "{\"cod\":\"200\",\"city\":{\"timezone\":3600},\"list\":[]}";
        assertThatThrownBy(() -> client.consulter(0, 0)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void gererLaCleInvalideSansExposerLaRequete() {
        serveur.statut = 401;
        assertThatThrownBy(() -> client.consulter(0, 0)).hasMessageContaining("clé météo")
                .hasMessageNotContaining("cle-fictive-test").hasMessageNotContaining("appid");
    }

    @Test void gererLeQuotaDepasse() {
        serveur.statut = 429;
        assertThatThrownBy(() -> client.consulter(0, 0)).hasMessageContaining("quota");
    }

    @Test void refuserUnAppelSansCle() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertThatThrownBy(() -> client.consulter(0, 0)).hasMessageContaining("configurée");
        assertThat(serveur.appels.get()).isZero();
    }

    @Test void appliquerLeDelaiDeLecture() {
        serveur.delaiMillis = 1500;
        assertThatThrownBy(() -> client.consulter(0, 0)).hasMessageContaining("délai prévu");
    }
}
