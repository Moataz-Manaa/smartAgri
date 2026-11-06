package com.example.backend.dto.pagination;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

// T représente le type des éléments : utilisateur, parcelle, etc.
@Getter
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> contenu;
    private int page;
    private int taille;
    private long totalElements;
    private int totalPages;
}
