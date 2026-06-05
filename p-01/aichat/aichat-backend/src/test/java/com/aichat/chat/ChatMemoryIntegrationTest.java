package com.aichat.chat;

import com.aichat.chat.dto.ChatRequest;
import com.aichat.auth.AuthSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatMemoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Test
    void secondMessage_shouldSendHistoryToPython() throws Exception {
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        String userId = "u1";
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthSession.USER_ID_KEY, userId);

        // Expectations must be registered before any actual requests are made.
        // First AI reply
        mockServer.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("http://localhost:5000/api/chat"))
                .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess("{\"response\":\"r1\"}", MediaType.APPLICATION_JSON));

        // Second AI reply: verify request includes previous user/assistant messages
        mockServer.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("http://localhost:5000/api/chat"))
                .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.content().string(org.hamcrest.Matchers.containsString("\"messages\"")))
                .andExpect(MockRestRequestMatchers.content().string(org.hamcrest.Matchers.containsString("\"content\":\"m1\"")))
                .andExpect(MockRestRequestMatchers.content().string(org.hamcrest.Matchers.containsString("\"content\":\"r1\"")))
                .andRespond(MockRestResponseCreators.withSuccess("{\"response\":\"r2\"}", MediaType.APPLICATION_JSON));

        ChatRequest first = new ChatRequest();
        first.setConversationId(null); // backward-compat path: service auto-creates a conversation
        first.setMessage("m1");

        String firstResponseJson = mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Parse conversationId from first response JSON (simple extraction via jsonPath would require ResultActions reuse).
        String convId = objectMapper.readTree(firstResponseJson).get("conversationId").asText();

        ChatRequest second = new ChatRequest();
        second.setConversationId(convId);
        second.setMessage("m2");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("r2"));

        mockServer.verify();
    }

    @Test
    void python500_shouldReturnReadableError() throws Exception {
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthSession.USER_ID_KEY, "u500");

        mockServer.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("http://localhost:5000/api/chat"))
                .andRespond(MockRestResponseCreators.withServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Failed to get response from DeepSeek API\"}"));

        ChatRequest req = new ChatRequest();
        req.setConversationId(null);
        req.setMessage("m1");

        mockMvc.perform(post("/api/chat")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Failed to get response from DeepSeek API"));

        mockServer.verify();
    }
}

