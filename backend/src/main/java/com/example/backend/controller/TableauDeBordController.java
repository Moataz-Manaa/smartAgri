package com.example.backend.controller;

import com.example.backend.dto.tableaudebord.*;
import com.example.backend.service.TableauDeBordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tableau-de-bord")
@RequiredArgsConstructor
public class TableauDeBordController {
    private final TableauDeBordService service;

    @GetMapping("/synthese")
    public SyntheseResponse consulterSynthese(@Valid @ModelAttribute TableauDeBordFiltreRequest filtre) {
        return service.consulterSynthese(filtre);
    }

    @GetMapping("/activites")
    public List<ActiviteRecenteResponse> consulterActivites(@Valid @ModelAttribute TableauDeBordFiltreRequest filtre) {
        return service.consulterActivites(filtre);
    }

    @GetMapping("/alertes")
    public AlertesTableauDeBordResponse consulterAlertes(@Valid @ModelAttribute TableauDeBordFiltreRequest filtre) {
        return service.consulterAlertes(filtre);
    }
}
