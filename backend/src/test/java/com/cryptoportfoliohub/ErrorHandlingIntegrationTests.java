package com.cryptoportfoliohub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, ErrorHandlingTestConfiguration.class})
class ErrorHandlingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestReturnsProblemDetailsAndRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-ID"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.requestId"));
    }

    @Test
    void validationErrorUsesAStableCodeAndSafeFieldErrors() throws Exception {
        mockMvc.perform(post("/test/errors/validation")
                        .with(user("test-user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value."));
    }

    @Test
    void accessDeniedReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/test/errors/forbidden").with(user("test-user")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void missingResourceReturnsProblemDetails() throws Exception {
        mockMvc.perform(get("/test/errors/not-found").with(user("test-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void providerErrorReturnsOnlyItsSafeCategory() throws Exception {
        mockMvc.perform(get("/test/errors/provider").with(user("test-user")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.providerCategory").value("INVALID_RESPONSE"))
                .andExpect(jsonPath("$.detail").value("A connected provider could not complete the request."));
    }

    @Test
    void persistenceErrorDoesNotExposeDatabaseDetails() throws Exception {
        String body = mockMvc.perform(get("/test/errors/persistence").with(user("test-user")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PERSISTENCE_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("select secret", "database-password", "stackTrace");
    }

    @Test
    void unexpectedErrorDoesNotExposeExceptionDetails() throws Exception {
        String body = mockMvc.perform(get("/test/errors/unexpected").with(user("test-user")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("internal-stack-secret", "IllegalStateException", "stackTrace");
    }
}
