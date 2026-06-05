package com.aichat.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void login_thenMe_thenLogout() throws Exception {
        // 1) me => not logged in
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false));

        // 2) request qr => state
        String qrJson = mockMvc.perform(get("/api/auth/wechat/qr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode qr = objectMapper.readTree(qrJson);
        String state = qr.get("state").asText();

        MockHttpSession session = new MockHttpSession();

        // 3) mock confirm => session logged in
        mockMvc.perform(post("/api/auth/wechat/mock/confirm")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"" + state + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.userId", notNullValue()));

        // 4) me => logged in
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.userId", notNullValue()));

        // 5) logout => 204, then me => not logged in
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false));
    }
}

