package com.example.backend;

import com.example.backend.config.MeteoConfig;
import com.example.backend.service.OpenWeatherClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

// Contrôle optionnel : deux appels réels avec le point public de Sfax.
@EnabledIfEnvironmentVariable(named = "SMARTAGRI_LIVE_METEO_TEST", matches = "true")
class OpenWeatherLiveTests {
    @Test void convertirUneReponseReelleAvecLaCleConfiguree() {
        String cle = System.getenv("OPENWEATHER_API_KEY");
        assertThat(cle != null && !cle.isBlank()).isTrue();
        OpenWeatherClient client = new OpenWeatherClient(new MeteoConfig()
                .meteoRestClient("https://api.openweathermap.org/data/2.5", 3, 10), Clock.systemUTC());
        ReflectionTestUtils.setField(client, "apiKey", cle);
        var donnees = client.consulter(34.74, 10.76);
        assertThat(donnees.getConditions().getDateDonnees()).isNotNull();
        assertThat(donnees.getConditions().getTemperatureCelsius()).isNotNull();
        assertThat(donnees.getPrevisions()).hasSizeBetween(1, 40);
        assertThat(donnees.getPrevisions().getFirst().getDatePrevision()).isAfter(donnees.getRecupereesLe());
    }
}
