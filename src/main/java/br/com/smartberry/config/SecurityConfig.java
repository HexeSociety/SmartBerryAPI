package br.com.smartberry.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Configuração de segurança da API.
 *
 * - Rotas usadas pelo ESP32 são públicas (não requerem token Firebase).
 * - Rotas usadas pelo aplicativo Android exigem token Firebase válido.
 * - Falta ou token inválido devolve 401.
 * - Inicialização do Firebase será feita via variável de ambiente GOOGLE_APPLICATION_CREDENTIALS.
 */
@Configuration
@Slf4j
public class SecurityConfig {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Filtro que valida o token Firebase somente nas rotas protegidas.
     */
    static class FirebaseAuthFilter extends OncePerRequestFilter {
        // Rotas públicas do ESP32
        // Rotas públicas do ESP32 - verificadas manualmente
        private boolean isPublic(HttpServletRequest request) {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            // POST /api/sensores
            if (HttpMethod.POST.name().equals(method) && "/api/sensores".equals(uri)) {
                return true;
            }
            // GET /api/comandos/pendentes
            if (HttpMethod.GET.name().equals(method) && "/api/comandos/pendentes".equals(uri)) {
                return true;
            }
            // PATCH /api/comandos/{id}/executado
            if (HttpMethod.PATCH.name().equals(method) && uri.matches("/api/comandos/[^/]+/executado")) {
                return true;
            }
            return false;
        }

        private boolean isApiRequest(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path != null && path.startsWith("/api/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            // Se a requisição não for da API, ignora o filtro de autenticação Firebase
            if (!isApiRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (isPublic(request)) {
                filterChain.doFilter(request, response);
                return;
            }


            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
                String idToken = authHeader.substring(BEARER_PREFIX.length());
                try {
                    // A inicialização do FirebaseApp será feita externamente via GOOGLE_APPLICATION_CREDENTIALS.
                    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(decoded.getUid(), null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    log.warn("Falha na verificação do token Firebase: {}", e.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Firebase ID token");
                    return;
                }
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Firebase ID token");
                return;
            }
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Entry point que devolve 401 quando a autenticação falha.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(new FirebaseAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Rotas públicas do ESP32
                        .requestMatchers(HttpMethod.POST, "/api/sensores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comandos/pendentes").permitAll()
                                .requestMatchers(HttpMethod.PATCH, "/api/comandos/*/executado").permitAll()
                        // Rotas protegidas do Android
                        .requestMatchers(HttpMethod.POST, "/api/comandos").authenticated()
                        // Qualquer outro /api/** requer autenticação
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
