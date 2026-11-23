package com.example.backend.controller;

import com.example.backend.dto.alerte.AlerteFiltreRequest;
import com.example.backend.dto.alerte.AlerteResponse;
import com.example.backend.dto.alerte.CompteurAlertesResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.AlerteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alertes")
@RequiredArgsConstructor
public class AlerteController {
    private final AlerteService alerteService;

    @GetMapping("/pagination")
    public PageResponse<AlerteResponse> consulterPage(@Valid @ModelAttribute AlerteFiltreRequest filtre,
                                                     @Valid @ModelAttribute PaginationRequest pagination) {
        return alerteService.consulterPage(filtre, PageRequest.of(pagination.getPage(), pagination.getTaille()));
    }

    @GetMapping("/compteur")
    public CompteurAlertesResponse consulterCompteur(@RequestParam(value = "parcelleId", required = false) Long parcelleId) {
        return alerteService.consulterCompteur(parcelleId);
    }

    @GetMapping("/{id}")
    public AlerteResponse consulterParId(@PathVariable("id") Long id) {
        return alerteService.consulterParId(id);
    }

    @PutMapping("/{id}/lecture")
    public AlerteResponse marquerLue(@PathVariable("id") Long id) {
        return alerteService.marquerLue(id);
    }
}
