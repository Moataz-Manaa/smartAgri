package com.example.backend.dto.erreur;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class ErreurResponse {

    private int statut;

    private String message;

    private Map<String, String> erreurs = new LinkedHashMap<>();
}