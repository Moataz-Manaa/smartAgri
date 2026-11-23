package com.example.backend.service;

import com.example.backend.dto.meteo.MeteoResponse;

public interface AlerteCalculService {
    void evaluer(Long parcelleId, MeteoResponse meteo);
    void expirer();
}
