package com.example.backend.controller;

import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.dto.parcelle.ParcelleRequest;
import com.example.backend.dto.parcelle.ParcelleResponse;
import com.example.backend.service.ParcelleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parcelles")
@RequiredArgsConstructor
public class ParcelleController {

    private final ParcelleService parcelleService;

    @GetMapping("/pagination")
    public PageResponse<ParcelleResponse> consulterPage(
            @Valid @ModelAttribute PaginationRequest pagination) {
        return parcelleService.consulterPage(
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("id").descending()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParcelleResponse creer(
            @Valid @RequestBody ParcelleRequest request) {
        return parcelleService.creer(request);
    }

    @PutMapping("/{id}")
    public ParcelleResponse modifier(
            @PathVariable("id") Long id,
            @Valid @RequestBody ParcelleRequest request) {
        return parcelleService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public ParcelleResponse consulterParId(
            @PathVariable("id") Long id) {
        return parcelleService.consulterParId(id);
    }

    @GetMapping
    public List<ParcelleResponse> consulterToutes() {
        return parcelleService.consulterToutes();
    }

    @GetMapping("/exploitation/{exploitationId}")
    public List<ParcelleResponse> consulterParExploitation(
            @PathVariable("exploitationId") Long exploitationId) {
        return parcelleService.consulterParExploitation(exploitationId);
    }
}
