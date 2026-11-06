package com.example.backend.config;

import com.example.backend.service.TokenService;
import com.example.backend.service.CompteKeycloakService;
import com.example.backend.enums.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.keycloak.representations.idm.UserRepresentation;

import java.io.IOException;
import java.text.ParseException;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class KeycloakFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final CompteKeycloakService compteKeycloakService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null ||
                !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {

            envoyerErreur(response, 401, "Token absent ou format incorrect");
            return;
        }

        String token = authorization.substring(7).trim();

        if (token.isEmpty()) {
            envoyerErreur(response, 401, "Token absent");
            return;
        }

        JWTClaimsSet informations;

        try {
            informations = tokenService.verifier(token);
        } catch (ParseException | BadJOSEException exception) {
            envoyerErreur(response, 401, "Token invalide ou expire");
            return;
        } catch (JOSEException exception) {
            envoyerErreur(
                    response,
                    503,
                    "Verification du token temporairement indisponible"
            );
            return;
        }

        String keycloakId = informations.getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            envoyerErreur(response, 401, "Identite invalide");
            return;
        }

        Role role;
        try {
            // Vérifier l'état actuel : un ancien JWT ne conserve pas un accès retiré.
            UserRepresentation compte = compteKeycloakService.consulter(keycloakId);
            if (!Boolean.TRUE.equals(compte.isEnabled())) {
                envoyerErreur(response, 401, "Compte desactive");
                return;
            }
            role = compteKeycloakService.consulterRole(keycloakId);
        } catch (ResponseStatusException exception) {
            int statut = exception.getStatusCode().value();
            if (statut == 404) {
                envoyerErreur(response, 401, "Compte introuvable");
            } else if (statut == 409) {
                envoyerErreur(response, 403, "Roles du compte a verifier");
            } else {
                envoyerErreur(response, 503, "Verification du compte temporairement indisponible");
            }
            return;
        }
        if (role == null) {
            envoyerErreur(response, 403, "Role metier requis");
            return;
        }
        request.setAttribute("utilisateurConnecte", informations);
        request.setAttribute("roleConnecte", role);

        filterChain.doFilter(request, response);
    }

    private void envoyerErreur(
            HttpServletResponse response,
            int statut,
            String message) throws IOException {

        response.setStatus(statut);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (statut == 401) {
            response.setHeader(
                    "WWW-Authenticate",
                    "Bearer realm=\"smartagri\""
            );
        }

        response.getWriter().write(
                "{\"statut\":" + statut +
                ",\"message\":\"" + message +
                "\",\"erreurs\":{}}"
        );
    }
}
