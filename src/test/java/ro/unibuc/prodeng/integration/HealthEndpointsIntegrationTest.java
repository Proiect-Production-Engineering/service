package ro.unibuc.prodeng.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the health endpoint ({@code /api/health}).
 * No authentication required.
 */
@DisplayName("Health endpoint integration tests (IT)")
class HealthEndpointsIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthCheck_returns200WithStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("SafeTransfer service")))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
