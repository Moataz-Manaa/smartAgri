package com.example.backend.exception;

import com.example.backend.dto.erreur.ErreurResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErreurResponse> gererValidation(
            MethodArgumentNotValidException exception) {

        ErreurResponse response = new ErreurResponse();

        response.setStatut(HttpStatus.BAD_REQUEST.value());
        response.setMessage("Les données envoyées sont invalides");

        for (FieldError erreur :
                exception.getBindingResult().getFieldErrors()) {

            response.getErreurs().put(
                    erreur.getField(),
                    erreur.getDefaultMessage()
            );
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErreurResponse> gererErreurHttp(
            ResponseStatusException exception) {

        ErreurResponse response = new ErreurResponse();

        response.setStatut(exception.getStatusCode().value());
        response.setMessage(exception.getReason());

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(response);
    }
}