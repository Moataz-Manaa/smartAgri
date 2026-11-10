package com.example.backend.controller;

import com.example.backend.dto.historique.ActiviteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.enums.TypeActivite;
import com.example.backend.service.HistoriqueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/historique")
@RequiredArgsConstructor
public class HistoriqueController {
    private final HistoriqueService historiqueService;

    @GetMapping("/pagination")
    public PageResponse<ActiviteResponse> consulterPage(@RequestParam("plantationId") Long plantationId,
            @RequestParam(value = "type", required = false) TypeActivite type,
            @RequestParam(value = "dateDebut", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(value = "dateFin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @Valid @ModelAttribute PaginationRequest pagination) {
        return historiqueService.consulterPage(plantationId, type, dateDebut, dateFin,
                PageRequest.of(pagination.getPage(), pagination.getTaille()));
    }
}
