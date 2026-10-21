package com.digitalgrader.auth;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.common.ApiErrors;
import com.digitalgrader.users.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final AuthService sessions;
    private final UserRepository users;
    private final ObjectMapper mapper;
    public JwtFilter(TokenService tokens, UserRepository users, ObjectMapper mapper, AuthService sessions) {
        this.sessions = sessions; this.tokens = tokens; this.users = users; this.mapper = mapper;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ") || header.length() > 4096) throw new IllegalArgumentException();
                var claims = tokens.verify(header.substring(7));
                var user = users.findById(Long.parseLong(claims.getSubject())).orElseThrow(IllegalArgumentException::new);
                if (!sessions.isSessionActive(user.id(), claims.get("session", String.class))) throw new IllegalArgumentException();
                if (!user.role().name().equals(claims.get("role", String.class))) throw new IllegalArgumentException();
                var auth = new UsernamePasswordAuthenticationToken(user.profile(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException error) {
                SecurityContextHolder.clearContext();
                ApiErrors.write(mapper, response, HttpStatus.UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

