package com.aichat.chat.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Client for the local Python AI gateway.
 * <p>
 * The Python service hides vendor-specific details (DeepSeek API) and allows us to mock it in tests.
 */
@Component
public class PythonChatClient {

    private static final String PYTHON_API_URL = "http://localhost:5000/api/chat";
    private static final String PYTHON_API_STREAM_URL = "http://localhost:5000/api/chat/stream";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public PythonChatClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> chatWithMessages(List<LlmMessage> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        try {
            return restTemplate.postForObject(PYTHON_API_URL, body, Map.class);
        } catch (HttpStatusCodeException e) {
            // Python may respond 5xx when DeepSeek is rate-limited/network-failed.
            // RestTemplate throws in that case; we want to surface a readable error to callers.
            String raw = e.getResponseBodyAsString();
            try {
                Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
                if (parsed.get("error") == null) {
                    parsed.put("error", "Python service error: HTTP " + e.getStatusCode());
                }
                return parsed;
            } catch (Exception ignored) {
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("error", "Python service error: HTTP " + e.getStatusCode());
                fallback.put("details", raw);
                return fallback;
            }
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("error", "Failed to call Python service: " + e.getMessage());
            return fallback;
        }
    }

    /**
     * Stream tokens from the Python SSE endpoint.
     * <p>
     * Python endpoint emits SSE blocks separated by a blank line, with fields:
     * - event: token|done|error
     * - data: JSON (e.g. {"delta":"..."})
     */
    public void streamChatWithMessages(
            List<LlmMessage> messages,
            Consumer<String> onToken,
            Consumer<String> onError,
            Runnable onDone
    ) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onToken, "onToken");
        Objects.requireNonNull(onError, "onError");
        Objects.requireNonNull(onDone, "onDone");

        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);

        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            onError.accept("Failed to serialize request: " + e.getMessage());
            return;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PYTHON_API_STREAM_URL))
                .timeout(Duration.ofMinutes(6))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                String raw = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                onError.accept("Python service error: HTTP " + status + (raw.isBlank() ? "" : (", body=" + raw)));
                return;
            }

            try (InputStream is = resp.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                String event = null;
                String data = null;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        dispatchSseEvent(event, data, onToken, onError, onDone);
                        event = null;
                        data = null;
                        continue;
                    }

                    if (line.startsWith("event:")) {
                        event = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        data = line.substring("data:".length()).trim();
                    }
                }

                // Flush last event if stream ended without a blank line.
                dispatchSseEvent(event, data, onToken, onError, onDone);
            }
        } catch (Exception e) {
            onError.accept("Failed to stream from Python service: " + e.getMessage());
        }
    }

    private void dispatchSseEvent(
            String event,
            String data,
            Consumer<String> onToken,
            Consumer<String> onError,
            Runnable onDone
    ) {
        if (event == null || event.isBlank()) return;

        try {
            if ("token".equals(event)) {
                if (data == null || data.isBlank()) return;
                Map<String, Object> parsed = objectMapper.readValue(data, new TypeReference<>() {});
                Object delta = parsed.get("delta");
                if (delta instanceof String s && !s.isBlank()) {
                    onToken.accept(s);
                }
            } else if ("done".equals(event)) {
                onDone.run();
            } else if ("error".equals(event)) {
                if (data == null || data.isBlank()) {
                    onError.accept("Python stream error");
                    return;
                }
                Map<String, Object> parsed = objectMapper.readValue(data, new TypeReference<>() {});
                Object err = parsed.get("error");
                onError.accept(err instanceof String s ? s : "Python stream error");
            }
        } catch (Exception e) {
            onError.accept("Failed to parse SSE event: " + e.getMessage());
        }
    }
}

