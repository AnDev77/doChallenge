package com.fitmeet.briefing.generation;

import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Calls a configured Spring AI provider; an absent model fails closed at invocation. */
@Component
public class SpringAiBriefingModel implements BriefingModel {
    private final ObjectProvider<ChatModel> models;

    public SpringAiBriefingModel(ObjectProvider<ChatModel> models) {
        this.models = models;
    }

    @Override
    public String complete(String systemInstruction, String evidenceInput) {
        var model = models.getIfAvailable();
        if (model == null) throw new IllegalStateException("Briefing chat model is not configured");
        var response = model.call(new Prompt(List.of(
                new SystemMessage(systemInstruction), new UserMessage(evidenceInput))));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Briefing chat model returned no output");
        }
        return response.getResult().getOutput().getText();
    }
}
