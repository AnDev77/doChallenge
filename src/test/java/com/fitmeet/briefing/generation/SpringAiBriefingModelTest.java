package com.fitmeet.briefing.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class SpringAiBriefingModelTest {
    @Test
    void sendsSeparateRolesAndReturnsOnlyModelText() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ChatModel chat = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(provider.getIfAvailable()).thenReturn(chat);
        when(chat.call(any(Prompt.class))).thenAnswer(call -> {
            Prompt prompt = call.getArgument(0);
            assertThat(prompt.getSystemMessage().getText()).isEqualTo("Only evidence");
            assertThat(prompt.getUserMessage().getText()).isEqualTo("Untrusted filing");
            return response;
        });
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn("{\"summary\":\"검토 필요\",\"citation_ids\":[1]}");

        assertThat(new SpringAiBriefingModel(provider).complete("Only evidence", "Untrusted filing"))
                .contains("검토 필요");
    }

    @Test
    void missingProviderFailsBeforeAnyCall() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        assertThatThrownBy(() -> new SpringAiBriefingModel(provider).complete("system", "evidence"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }
}
