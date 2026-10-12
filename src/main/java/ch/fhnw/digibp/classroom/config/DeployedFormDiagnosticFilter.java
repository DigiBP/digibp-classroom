package ch.fhnw.digibp.classroom.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DeployedFormDiagnosticFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(DeployedFormDiagnosticFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().endsWith("/deployed-form");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        log.info("Deployed-form request started: {}", request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            log.error("Deployed-form request failed", ex);
            throw ex;
        } finally {
            log.info("Deployed-form request completed: HTTP {}", response.getStatus());
        }
    }
}
