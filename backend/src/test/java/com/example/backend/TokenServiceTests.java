package com.example.backend;

import com.example.backend.service.impl.TokenServiceImpl;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

// Signatures RSA réelles et serveur JWKS local éphémère.
class TokenServiceTests {
    static HttpServer serveur;
    static RSAKey cle;
    static TokenServiceImpl service;
    static String issuer;

    @BeforeAll
    static void preparer() throws Exception {
        cle = new RSAKeyGenerator(2048).keyID("test-key").generate();
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/realms/test/protocol/openid-connect/certs", echange -> {
            byte[] corps = new JWKSet(cle.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().set("Content-Type", "application/json");
            echange.sendResponseHeaders(200, corps.length);
            try (var sortie = echange.getResponseBody()) {
                sortie.write(corps);
            }
        });
        serveur.start();
        issuer = "http://127.0.0.1:" + serveur.getAddress().getPort() + "/realms/test";
        service = new TokenServiceImpl(issuer, "smartagri-api");
    }

    @AfterAll
    static void fermer() throws Exception {
        if (service != null) service.fermer();
        if (serveur != null) serveur.stop(0);
    }

    @Test
    void accepterTokenSignePourCetteApi() throws Exception {
        assertThat(service.verifier(signer(claims().build(), cle)).getSubject()).isEqualTo("utilisateur");
    }

    @Test
    void refuserTokenExpire() throws Exception {
        String token = signer(claims().expirationTime(Date.from(Instant.now().minusSeconds(120))).build(), cle);
        assertThatThrownBy(() -> service.verifier(token)).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void refuserAutreAudience() throws Exception {
        String token = signer(claims().audience("autre-api").build(), cle);
        assertThatThrownBy(() -> service.verifier(token)).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void refuserAutreEmetteur() throws Exception {
        String token = signer(claims().issuer("http://autre-emetteur.test").build(), cle);
        assertThatThrownBy(() -> service.verifier(token)).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void refuserSignatureInvalide() throws Exception {
        RSAKey fausseCle = new RSAKeyGenerator(2048).keyID("test-key").generate();
        String token = signer(claims().build(), fausseCle);
        assertThatThrownBy(() -> service.verifier(token)).isInstanceOf(BadJOSEException.class);
    }

    @Test
    void refuserTokenSansExpirationEtTokenIdentite() throws Exception {
        String sansExpiration = signer(claims().expirationTime(null).build(), cle);
        String identite = signer(claims().claim("typ", "ID").build(), cle);
        assertThatThrownBy(() -> service.verifier(sansExpiration)).isInstanceOf(BadJOSEException.class);
        assertThatThrownBy(() -> service.verifier(identite)).isInstanceOf(BadJOSEException.class);
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(issuer).audience("smartagri-api")
                .subject("utilisateur").claim("typ", "Bearer")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private String signer(JWTClaimsSet claims, RSAKey cleSignature) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(cleSignature));
        return jwt.serialize();
    }
}
