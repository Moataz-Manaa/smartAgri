package com.example.backend.controller;

import com.example.backend.dto.campagne.CampagneRequest;
import com.example.backend.dto.campagne.CampagneResponse;
import com.example.backend.dto.campagne.StatutCampagneRequest;
import com.example.backend.dto.campagne.ParcellesCampagneRequest;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.enums.StatutCampagne;
import com.example.backend.service.CampagneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/campagnes")
@RequiredArgsConstructor
public class CampagneController {
    private final CampagneService campagneService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampagneResponse creer(@Valid @RequestBody CampagneRequest request) {
        return campagneService.creer(request);
    }

    @PutMapping("/{id}")
    public CampagneResponse modifier(@PathVariable("id") Long id, @Valid @RequestBody CampagneRequest request) {
        return campagneService.modifier(id, request);
    }

    @PutMapping("/{id}/statut")
    public CampagneResponse changerStatut(@PathVariable("id") Long id, @Valid @RequestBody StatutCampagneRequest request) {
        return campagneService.changerStatut(id, request.getStatut());
    }

    @GetMapping("/{id}")
    public CampagneResponse consulterParId(@PathVariable("id") Long id) {
        return campagneService.consulterParId(id);
    }

    @PutMapping("/{id}/parcelles")
    public CampagneResponse associerParcelles(@PathVariable("id") Long id,
            @Valid @RequestBody ParcellesCampagneRequest request) {
        return campagneService.associerParcelles(id, request.getParcelleIds());
    }

    @GetMapping
    public List<CampagneResponse> consulterToutes(@RequestParam(value = "statut", required = false) StatutCampagne statut) {
        return campagneService.consulterToutes(statut);
    }

    @GetMapping("/pagination")
    public PageResponse<CampagneResponse> consulterPage(@Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(value = "statut", required = false) StatutCampagne statut) {
        return campagneService.consulterPage(PageRequest.of(pagination.getPage(), pagination.getTaille(),
                Sort.by("dateDebut").descending().and(Sort.by("id").descending())), statut);
    }
}
