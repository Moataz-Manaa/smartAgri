package com.example.backend.controller;

import com.example.backend.dto.variete.VarieteRequest;
import com.example.backend.dto.variete.VarieteResponse;
import com.example.backend.dto.pagination.PageResponse;
import com.example.backend.dto.pagination.PaginationRequest;
import com.example.backend.service.VarieteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/varietes")
@RequiredArgsConstructor
public class VarieteController {
    private final VarieteService varieteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VarieteResponse creer(@Valid @RequestBody VarieteRequest request) {
        return varieteService.creer(request);
    }

    @PutMapping("/{id}")
    public VarieteResponse modifier(@PathVariable("id") Long id, @Valid @RequestBody VarieteRequest request) {
        return varieteService.modifier(id, request);
    }

    @GetMapping("/{id}")
    public VarieteResponse consulterParId(@PathVariable("id") Long id) {
        return varieteService.consulterParId(id);
    }

    @GetMapping
    public List<VarieteResponse> consulterToutes(@RequestParam(value = "cultureId", required = false) Long cultureId) {
        return varieteService.consulterToutes(cultureId);
    }

    @GetMapping("/pagination")
    public PageResponse<VarieteResponse> consulterPage(@Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(value = "cultureId", required = false) Long cultureId) {
        return varieteService.consulterPage(PageRequest.of(pagination.getPage(), pagination.getTaille(),
                Sort.by("id").descending()), cultureId);
    }
}
