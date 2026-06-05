package com.aichat.chat.service;

import com.aichat.chat.llm.LlmMessage;
import com.aichat.memory.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Build the LLM messages array from long-term summary + recent window.
 * <p>
 * This builder is intentionally deterministic to make it easy to test:
 * the output order and roles should be stable given the same inputs.
 */
public class MemoryContextBuilder {

    public List<LlmMessage> build(String summary, List<Message> lastK, String userMessage) {
        List<LlmMessage> out = new ArrayList<>();

        if (summary != null && !summary.isBlank()) {
            out.add(new LlmMessage(
                    "system",
                    "以下是该会话的长期记忆摘要（可能不完整，以最新对话为准）：\n" + summary
            ));
        }

        for (Message m : lastK) {
            out.add(new LlmMessage(m.getRole(), m.getContent()));
        }

        out.add(new LlmMessage("user", userMessage));
        return out;
    }
}

