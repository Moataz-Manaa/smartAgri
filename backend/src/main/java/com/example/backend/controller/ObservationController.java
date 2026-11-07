package com.example.backend.controller;

import com.example.backend.dto.observation.ObservationRequest;
import com.example.backend.dto.observation.ObservationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.ObservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/observations")
@RequiredArgsConstructor
public class ObservationController {
    private final ObservationService observationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationResponse creer(@Valid @RequestBody ObservationRequest request) {
        return observationService.creer(request);
    }

    @PutMapping("/{id}")
    public ObservationResponse modifier(@PathVariable("id") Long id,
            @Valid @RequestBody ObservationRequest request) {
        return observationService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public ObservationResponse consulterParId(@PathVariable("id") Long id) {
        return observationService.consulterParId(id);
    }

    @GetMapping("/pagination")
    public PageResponse<ObservationResponse> consulterPage(@RequestParam("plantationId") Long plantationId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return observationService.consulterPage(plantationId,
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("dateObservation").descending().and(Sort.by("id").descending())));
    }
}
