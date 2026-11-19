package com.schoolproject.ChatAPP.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces Spring Security 6's deferred CsrfToken to be materialized so the
 * XSRF-TOKEN cookie and X-XSRF-TOKEN response header are consistently exposed to SPAs.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            String tokenValue = csrfToken.getToken();
            if (tokenValue != null && !tokenValue.isEmpty()) {
                response.setHeader("X-XSRF-TOKEN", tokenValue);
            }
        }

        filterChain.doFilter(request, response);
    }
}
