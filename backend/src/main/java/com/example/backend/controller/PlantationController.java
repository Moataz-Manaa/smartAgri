package com.example.backend.controller;

import com.example.backend.dto.plantation.PlantationRequest;
import com.example.backend.dto.plantation.PlantationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.enums.StatutPlantation;
import com.example.backend.service.PlantationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plantations")
@RequiredArgsConstructor
public class PlantationController {
    private final PlantationService plantationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlantationResponse creer(@Valid @RequestBody PlantationRequest request) {
        return plantationService.creer(request);
    }

    @PutMapping("/{id}")
    public PlantationResponse modifier(@PathVariable("id") Long id,
            @Valid @RequestBody PlantationRequest request) {
        return plantationService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public PlantationResponse consulterParId(@PathVariable("id") Long id) {
        return plantationService.consulterParId(id);
    }

    @GetMapping("/pagination")
    public PageResponse<PlantationResponse> consulterPage(@Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(value = "parcelleId", required = false) Long parcelleId,
            @RequestParam(value = "campagneId", required = false) Long campagneId,
            @RequestParam(value = "statut", required = false) StatutPlantation statut) {
        return plantationService.consulterPage(PageRequest.of(pagination.getPage(), pagination.getTaille(),
                Sort.by("id").descending()), parcelleId, campagneId, statut);
    }
}
