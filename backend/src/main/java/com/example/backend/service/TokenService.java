package com.example.backend.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;

import java.text.ParseException;

public interface TokenService {

    JWTClaimsSet verifier(String token)
            throws ParseException, BadJOSEException, JOSEException;
}