package com.aichat.memory.web;

import com.aichat.auth.AuthSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createConversationAndList_areIsolatedByUserId() throws Exception {
        String userA = "user-a";
        String userB = "user-b";
        MockHttpSession sessionA = new MockHttpSession();
        sessionA.setAttribute(AuthSession.USER_ID_KEY, userA);
        MockHttpSession sessionB = new MockHttpSession();
        sessionB.setAttribute(AuthSession.USER_ID_KEY, userB);

        mockMvc.perform(post("/api/conversations")
                        .session(sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TitleBody("A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("A"));

        mockMvc.perform(post("/api/conversations")
                        .session(sessionB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TitleBody("B"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/conversations").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("A"));

        mockMvc.perform(get("/api/conversations").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("B"));
    }

    static class TitleBody {
        public String title;

        TitleBody(String title) {
            this.title = title;
        }
    }
}

