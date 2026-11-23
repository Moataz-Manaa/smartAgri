package com.example.backend.service;

import com.example.backend.dto.meteo.MeteoResponse;

public interface MeteoService {
    MeteoResponse consulterParParcelle(Long parcelleId);
}
