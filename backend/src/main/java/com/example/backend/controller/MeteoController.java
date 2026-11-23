package com.example.backend.controller;

import com.example.backend.dto.meteo.MeteoResponse;
import com.example.backend.service.MeteoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parcelles")
@RequiredArgsConstructor
public class MeteoController {
    private final MeteoService meteoService;

    @GetMapping("/{id}/meteo")
    public MeteoResponse consulter(@PathVariable("id") Long id) {
        return meteoService.consulterParParcelle(id);
    }
}
