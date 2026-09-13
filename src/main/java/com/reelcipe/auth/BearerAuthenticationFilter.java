package com.reelcipe.auth;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.common.http.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;
    private final AuthService authService;

    public BearerAuthenticationFilter(JwtTokenService tokenService, AuthService authService) {
        this.tokenService = tokenService;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            JwtTokenService.AuthenticatedToken token = tokenService.parseAndVerify(
                    authorization.substring("Bearer ".length()).trim(), java.time.Instant.now());
            AuthenticatedUser user = authService.requireActiveSession(token);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/problem+json");
            String traceId = (String) request.getAttribute(TraceIdFilter.HEADER_NAME);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\","
                    + "\"status\":401,\"detail\":\"Invalid or inactive access token\","
                    + "\"instance\":\"" + request.getRequestURI() + "\","
                    + "\"traceId\":\"" + (traceId == null ? "" : traceId) + "\",\"errors\":[]}");
        }
    }
}
