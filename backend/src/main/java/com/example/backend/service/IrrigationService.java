package com.example.backend.service;

import com.example.backend.dto.irrigation.IrrigationRequest;
import com.example.backend.dto.irrigation.IrrigationResponse;
import com.example.backend.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import com.example.backend.dto.irrigation.TotalIrrigationResponse;

public interface IrrigationService {
    TotalIrrigationResponse consulterTotal(Long plantationId);
    IrrigationResponse creer(IrrigationRequest request);
    IrrigationResponse modifier(Long id, IrrigationRequest request);
    IrrigationResponse consulterParId(Long id);
    PageResponse<IrrigationResponse> consulterPage(Long plantationId, Pageable pagination);
}
