package br.com.renan.corebanking.authorization.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String TRANSACTION_ID_MDC_KEY = "transactionId";

    private static final Pattern TRANSACTION_PATH_PATTERN = Pattern.compile("^/transactions/([^/]+)$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        String transactionId = extractTransactionId(request.getRequestURI());

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        if (transactionId != null) {
            MDC.put(TRANSACTION_ID_MDC_KEY, transactionId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRANSACTION_ID_MDC_KEY);
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        String headerValue = request.getHeader(CORRELATION_ID_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return headerValue.trim();
    }

    private static String extractTransactionId(String requestUri) {
        Matcher matcher = TRANSACTION_PATH_PATTERN.matcher(requestUri);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }
}
