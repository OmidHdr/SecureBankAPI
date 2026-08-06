package ir.h0p3.securebankapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-test-secret-key-test-secret-key",
        "jwt.expiration=86400000",
        "jwt.refresh-expiration=604800000",
        "rate-limit.login.requests=2",
        "rate-limit.login.window=1m"
})
class RateLimitIntegrationTest {

    private final MockMvc mockMvc;

    RateLimitIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void filterRejectsBeforeControllerAfterConfiguredLimit() throws Exception {
        login().andExpect(status().isBadRequest())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        login().andExpect(status().isBadRequest())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        login().andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    private org.springframework.test.web.servlet.ResultActions login()
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }
}
