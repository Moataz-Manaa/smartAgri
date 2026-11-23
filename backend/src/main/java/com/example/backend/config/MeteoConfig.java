package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class MeteoConfig {
    @Bean
    public RestClient meteoRestClient(
            @Value("${app.meteo.base-url:https://api.openweathermap.org/data/2.5}") String baseUrl,
            @Value("${app.meteo.connexion-secondes:3}") int connexionSecondes,
            @Value("${app.meteo.lecture-secondes:5}") int lectureSecondes) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connexionSecondes)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(lectureSecondes));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    public Clock meteoHorloge() {
        return Clock.systemUTC();
    }
}
