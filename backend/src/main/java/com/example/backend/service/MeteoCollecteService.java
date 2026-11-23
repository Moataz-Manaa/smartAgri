package com.example.backend.service;

import com.example.backend.dto.meteo.MeteoResponse;
import com.example.backend.entity.Parcelle;

// Collecte interne partagée par l'API autorisée et le calcul planifié des alertes.
public interface MeteoCollecteService {
    MeteoResponse collecter(Parcelle parcelle);
}
