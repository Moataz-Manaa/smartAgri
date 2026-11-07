package com.example.backend.controller;

import com.example.backend.dto.fertilisation.FertilisationRequest;
import com.example.backend.dto.fertilisation.FertilisationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.FertilisationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fertilisations")
@RequiredArgsConstructor
public class FertilisationController {
    private final FertilisationService fertilisationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FertilisationResponse creer(@Valid @RequestBody FertilisationRequest request) {
        return fertilisationService.creer(request);
    }

    @PutMapping("/{id}")
    public FertilisationResponse modifier(@PathVariable("id") Long id,
            @Valid @RequestBody FertilisationRequest request) {
        return fertilisationService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public FertilisationResponse consulterParId(@PathVariable("id") Long id) {
        return fertilisationService.consulterParId(id);
    }

    @GetMapping("/pagination")
    public PageResponse<FertilisationResponse> consulterPage(@RequestParam("plantationId") Long plantationId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return fertilisationService.consulterPage(plantationId,
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("dateFertilisation").descending().and(Sort.by("id").descending())));
    }
}
