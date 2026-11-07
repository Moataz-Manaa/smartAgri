package com.example.backend.controller;

import com.example.backend.dto.irrigation.IrrigationRequest;
import com.example.backend.dto.irrigation.IrrigationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.IrrigationService;
import jakarta.validation.Valid;
import com.example.backend.dto.irrigation.TotalIrrigationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/irrigations")
@RequiredArgsConstructor
public class IrrigationController {
    private final IrrigationService irrigationService;

    @GetMapping("/total")
    public TotalIrrigationResponse consulterTotal(@RequestParam("plantationId") Long plantationId) {
        return irrigationService.consulterTotal(plantationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IrrigationResponse creer(@Valid @RequestBody IrrigationRequest request) {
        return irrigationService.creer(request);
    }

    @PutMapping("/{id}")
    public IrrigationResponse modifier(@PathVariable("id") Long id,
            @Valid @RequestBody IrrigationRequest request) {
        return irrigationService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public IrrigationResponse consulterParId(@PathVariable("id") Long id) {
        return irrigationService.consulterParId(id);
    }

    @GetMapping("/pagination")
    public PageResponse<IrrigationResponse> consulterPage(@RequestParam("plantationId") Long plantationId,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return irrigationService.consulterPage(plantationId,
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("dateIrrigation").descending().and(Sort.by("id").descending())));
    }
}
