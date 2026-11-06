package com.example.backend.service;

import com.example.backend.dto.variete.VarieteRequest;
import com.example.backend.dto.variete.VarieteResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface VarieteService {
    VarieteResponse creer(VarieteRequest request);
    VarieteResponse modifier(Long id, VarieteRequest request);
    VarieteResponse consulterParId(Long id);
    List<VarieteResponse> consulterToutes(Long cultureId);
    PageResponse<VarieteResponse> consulterPage(Pageable pagination, Long cultureId);
}
