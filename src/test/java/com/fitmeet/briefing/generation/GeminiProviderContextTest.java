package com.fitmeet.briefing.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Ensures the optional Gemini provider is selected without making an external request. */
@SpringBootTest
@ActiveProfiles("integration")
@TestPropertySource(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=test-only-placeholder",
        "spring.ai.google.genai.chat.options.model=gemini-3.5-flash-lite"
})
class GeminiProviderContextTest {
    @Autowired ChatModel chatModel;

    @Test
    void choosesGeminiChatModelWithoutCallingProvider() {
        assertThat(chatModel.getClass().getName()).contains("GoogleGenAiChatModel");
    }
}
