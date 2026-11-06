package com.example.backend.controller;

import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.dto.utilisateur.UtilisateurCreationRequest;
import com.example.backend.dto.utilisateur.UtilisateurModificationRequest;
import com.example.backend.dto.utilisateur.UtilisateurResponse;
import com.example.backend.service.UtilisateurService;
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
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping("/pagination")
    public PageResponse<UtilisateurResponse> consulterPage(
            @Valid @ModelAttribute PaginationRequest pagination) {
        return utilisateurService.consulterPage(
                PageRequest.of(pagination.getPage(), pagination.getTaille(),
                        Sort.by("id").descending()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UtilisateurResponse creer(
            @Valid @RequestBody UtilisateurCreationRequest request) {

        return utilisateurService.creer(request);
    }

    @PutMapping("/{id}")
    public UtilisateurResponse modifier(
            @PathVariable("id") Long id,
            @Valid @RequestBody UtilisateurModificationRequest request) {

        return utilisateurService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public UtilisateurResponse consulterParId(
            @PathVariable("id") Long id) {

        return utilisateurService.consulterParId(id);
    }

    @GetMapping
    public List<UtilisateurResponse> consulterTous() {
        return utilisateurService.consulterTous();
    }

    @PutMapping("/{id}/activer")
    public UtilisateurResponse activer(
            @PathVariable("id") Long id) {

        return utilisateurService.activer(id);
    }

    @PutMapping("/{id}/desactiver")
    public UtilisateurResponse desactiver(
            @PathVariable("id") Long id) {

        return utilisateurService.desactiver(id);
    }
}
