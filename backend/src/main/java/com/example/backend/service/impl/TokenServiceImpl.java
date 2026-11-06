package com.example.backend.service.impl;

import com.example.backend.service.TokenService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;
import java.io.Closeable;

@Service
public class TokenServiceImpl implements TokenService {

    private final DefaultJWTProcessor<SecurityContext> jwtProcessor;

    private final JWKSource<SecurityContext> sourceCles;

    public TokenServiceImpl(
            @Value("${app.keycloak.issuer}") String issuer,
            @Value("${app.keycloak.audience}") String audience)
            throws IOException {

        String adresseCles = issuer + "/protocol/openid-connect/certs";

        sourceCles = JWKSourceBuilder
                .<SecurityContext>create(
                        URI.create(adresseCles).toURL()
                )
                .build();

        jwtProcessor = new DefaultJWTProcessor<>();

        jwtProcessor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(
                        JWSAlgorithm.RS256,
                        sourceCles
                )
        );

        JWTClaimsSet informationsAttendues = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .claim("typ", "Bearer")
                .build();

        Set<String> champsObligatoires = new HashSet<>();
        champsObligatoires.add("sub");
        champsObligatoires.add("exp");
        champsObligatoires.add("iat");

DefaultJWTClaimsVerifier<SecurityContext> verificateur =
        new DefaultJWTClaimsVerifier<>(
                audience,
                informationsAttendues,
                champsObligatoires
        );

        verificateur.setMaxClockSkew(30);

        jwtProcessor.setJWTClaimsSetVerifier(verificateur);
    }

    @Override
    public JWTClaimsSet verifier(String token)
            throws ParseException, BadJOSEException, JOSEException {

        return jwtProcessor.process(token, null);
    }

@PreDestroy
public void fermer() throws IOException {
    if (sourceCles instanceof Closeable) {
        Closeable ressource = (Closeable) sourceCles;
        ressource.close();
    }
}
}