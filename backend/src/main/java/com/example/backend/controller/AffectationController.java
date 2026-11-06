package com.example.backend.controller;

import com.example.backend.dto.affectation.AffectationRequest;
import com.example.backend.dto.affectation.AffectationResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.AffectationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@RestController
@RequestMapping("/api/affectations")
@RequiredArgsConstructor
public class AffectationController {

    private final AffectationService affectationService;

    @GetMapping("/pagination")
    public PageResponse<AffectationResponse> consulterPage(
            @Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(value = "parcelleId", required = false) Long parcelleId) {
        return affectationService.consulterPage(
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("id").descending()), parcelleId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AffectationResponse creer(@Valid @RequestBody AffectationRequest request) {
        return affectationService.creer(request);
    }

    @GetMapping
    public List<AffectationResponse> consulterToutes(
            @RequestParam(value = "parcelleId", required = false) Long parcelleId) {
        return affectationService.consulterToutes(parcelleId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirer(@PathVariable("id") Long id) {
        affectationService.retirer(id);
    }
}
