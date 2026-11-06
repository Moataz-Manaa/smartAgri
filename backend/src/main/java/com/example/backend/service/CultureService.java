package com.example.backend.service;

import com.example.backend.dto.culture.CultureRequest;
import com.example.backend.dto.culture.CultureResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface CultureService {
    CultureResponse creer(CultureRequest request);
    CultureResponse modifier(Long id, CultureRequest request);
    CultureResponse consulterParId(Long id);
    List<CultureResponse> consulterToutes();
    PageResponse<CultureResponse> consulterPage(Pageable pagination);
}
