package com.example.backend.dto.meteo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

// Contrat du fournisseur. Ce DTO n'est jamais renvoyé à Angular.
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenWeatherResponse {
    private String cod;
    private Long dt;
    private Integer timezone;
    private Mesures main;
    private List<Description> weather;
    private Map<String, Double> rain;
    private Double pop;
    private List<OpenWeatherResponse> list;
    private Ville city;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mesures {
        private Double temp;
        private Double humidity;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Description {
        private String description;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ville {
        private Integer timezone;
    }
}
