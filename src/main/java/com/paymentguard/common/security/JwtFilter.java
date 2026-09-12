package com.paymentguard.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends GenericFilter {
    private final JwtService jwt;

    public JwtFilter(JwtService j) {
        jwt = j;
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest h = (HttpServletRequest) req;
        String auth = h.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(auth.substring(7));
                var a = new UsernamePasswordAuthenticationToken(c.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + c.get("role"))));
                SecurityContextHolder.getContext().setAuthentication(a);
            } catch (Exception ignored) {
            }
        }
        chain.doFilter(req, res);
    }
}
