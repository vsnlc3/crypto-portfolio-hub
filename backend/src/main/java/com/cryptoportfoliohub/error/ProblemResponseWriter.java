package com.cryptoportfoliohub.error;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;
    private final ProblemResponseFactory problemResponseFactory;

    public ProblemResponseWriter(JsonMapper jsonMapper, ProblemResponseFactory problemResponseFactory) {
        this.jsonMapper = jsonMapper;
        this.problemResponseFactory = problemResponseFactory;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(),
                problemResponseFactory.create(status, code, detail, request));
    }
}
