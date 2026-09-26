package com.cryptoportfoliohub;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.error.ResourceNotFoundException;

@RestController
@RequestMapping("/test/errors")
class ErrorHandlingTestController {

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException();
    }

    @PostMapping("/validation")
    void validation(@Valid @RequestBody ValidationRequest request) {
    }

    @GetMapping("/forbidden")
    @PreAuthorize("hasAuthority('portfolio:write')")
    String forbidden() {
        return "unreachable";
    }

    @GetMapping("/provider")
    void provider() {
        throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }

    @GetMapping("/persistence")
    void persistence() {
        throw new DataAccessResourceFailureException("select secret from credentials using database-password");
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("internal-stack-secret");
    }

    record ValidationRequest(@NotBlank String name) {
    }
}
