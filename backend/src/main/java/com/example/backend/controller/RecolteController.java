package com.example.backend.controller;

import com.example.backend.dto.recolte.RecolteRequest;
import com.example.backend.dto.recolte.RecolteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.RecolteService;
import jakarta.validation.Valid;
import com.example.backend.dto.recolte.TotalRecolteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recoltes")
@RequiredArgsConstructor
public class RecolteController {
    private final RecolteService recolteService;

    @GetMapping("/total")
    public TotalRecolteResponse consulterTotal(@RequestParam("plantationId") Long plantationId) {
        return recolteService.consulterTotal(plantationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecolteResponse creer(@Valid @RequestBody RecolteRequest request) {
        return recolteService.creer(request);
    }

    @PutMapping("/{id}")
    public RecolteResponse modifier(@PathVariable("id") Long id,
            @Valid @RequestBody RecolteRequest request) {
        return recolteService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public RecolteResponse consulterParId(@PathVariable("id") Long id) {
        return recolteService.consulterParId(id);
    }

    @GetMapping("/pagination")
    public PageResponse<RecolteResponse> consulterPage(@RequestParam("plantationId") Long plantationId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return recolteService.consulterPage(plantationId,
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("dateRecolte").descending().and(Sort.by("id").descending())));
    }
}
