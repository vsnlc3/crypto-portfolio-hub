package com.cryptoportfoliohub.error;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProblemResponseFactory {

    public Map<String, Object> create(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("code", code);
        problem.put("requestId", request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME));
        return problem;
    }
}
