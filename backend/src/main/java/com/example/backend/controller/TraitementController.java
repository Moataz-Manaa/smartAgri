package com.example.backend.controller;

import com.example.backend.dto.traitement.TraitementRequest;
import com.example.backend.dto.traitement.TraitementResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.TraitementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traitements")
@RequiredArgsConstructor
public class TraitementController {
    private final TraitementService traitementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TraitementResponse creer(@Valid @RequestBody TraitementRequest request) {
        return traitementService.creer(request);
    }

    @PutMapping("/{id}")
    public TraitementResponse modifier(@PathVariable("id") Long id,
            @Valid @RequestBody TraitementRequest request) {
        return traitementService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public TraitementResponse consulterParId(@PathVariable("id") Long id) {
        return traitementService.consulterParId(id);
    }

    @GetMapping("/pagination")
    public PageResponse<TraitementResponse> consulterPage(@RequestParam("plantationId") Long plantationId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return traitementService.consulterPage(plantationId,
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("dateTraitement").descending().and(Sort.by("id").descending())));
    }
}
