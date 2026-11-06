package com.example.backend.controller;

import com.example.backend.dto.culture.CultureRequest;
import com.example.backend.dto.culture.CultureResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.CultureService;
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
@RequestMapping("/api/cultures")
@RequiredArgsConstructor
public class CultureController {
    private final CultureService cultureService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CultureResponse creer(@Valid @RequestBody CultureRequest request) {
        return cultureService.creer(request);
    }

    @PutMapping("/{id}")
    public CultureResponse modifier(@PathVariable("id") Long id, @Valid @RequestBody CultureRequest request) {
        return cultureService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public CultureResponse consulterParId(@PathVariable("id") Long id) {
        return cultureService.consulterParId(id);
    }

    @GetMapping
    public List<CultureResponse> consulterToutes() {
        return cultureService.consulterToutes();
    }

    @GetMapping("/pagination")
    public PageResponse<CultureResponse> consulterPage(@Valid @ModelAttribute PaginationRequest pagination) {
        return cultureService.consulterPage(PageRequest.of(pagination.getPage(), pagination.getTaille(),
                Sort.by("id").descending()));
    }
}
