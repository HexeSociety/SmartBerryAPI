package br.com.smartberry.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

@Configuration
@Slf4j
public class SecurityConfig {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String IOT_HEADER = "X-IoT-Key";
    private static final String IOT_AUTHORITY = "IOT_TELEMETRIA";
    private static final String IOT_PREFIX = "/api/sensores/esp32/";

    static class FirebaseAuthFilter extends OncePerRequestFilter {
        private final String iotDeviceId;
        private final String iotApiKey;

        FirebaseAuthFilter(String iotDeviceId, String iotApiKey) {
            this.iotDeviceId = iotDeviceId;
            this.iotApiKey = iotApiKey;
        }

        // Rota de telemetria identificada: autenticação exclusiva do dispositivo.
        private boolean isIoTRequest(HttpServletRequest request) {
            return HttpMethod.POST.name().equals(request.getMethod())
                    && request.getRequestURI().matches("/api/sensores/esp32/[^/]+");
        }

        // Compatibilidade temporária com as rotas públicas originais.
        private boolean isPublic(HttpServletRequest request) {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            return (HttpMethod.POST.name().equals(method) && "/api/sensores".equals(uri))
                    || (HttpMethod.GET.name().equals(method) && "/api/comandos/pendentes".equals(uri))
                    || (HttpMethod.PATCH.name().equals(method) && uri.matches("/api/comandos/[^/]+/executado"));
        }

        private boolean isApiRequest(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path != null && path.startsWith("/api/");
        }

        private void authenticateIoT(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws IOException, ServletException {
            if (!StringUtils.hasText(iotDeviceId) || !StringUtils.hasText(iotApiKey)) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "IoT authentication not configured");
                return;
            }
            String key = request.getHeader(IOT_HEADER);
            if (key == null || !MessageDigest.isEqual(
                    iotApiKey.getBytes(StandardCharsets.UTF_8),
                    key.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing IoT key");
                return;
            }
            String requestedId = request.getRequestURI().substring(IOT_PREFIX.length());
            if (!iotDeviceId.equals(requestedId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "IoT device not authorized for this ID");
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    iotDeviceId, null, List.of(new SimpleGrantedAuthority(IOT_AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (!isApiRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (isIoTRequest(request)) {
                authenticateIoT(request, response, filterChain);
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
                    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            decoded.getUid(), null, Collections.emptyList());
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

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${SMARTBERRY_IOT_DEVICE_ID:}") String iotDeviceId,
                                           @Value("${SMARTBERRY_IOT_API_KEY:}") String iotApiKey) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(new FirebaseAuthFilter(iotDeviceId, iotApiKey),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/sensores/esp32/*").hasAuthority(IOT_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/api/sensores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comandos/pendentes").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/comandos/*/executado").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/comandos").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll());
        return http.build();
    }
}
