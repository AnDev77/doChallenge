package com.fitmeet.financechat;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/rooms")
public class FinanceChatController {
    private final FinanceChatService service;
    public FinanceChatController(FinanceChatService service) { this.service = service; }

    @GetMapping("/{symbol}/messages")
    public ApiResponse<List<FinanceMessageView>> messages(@PathVariable String symbol,
            @RequestParam(required = false) Long afterId) {
        return ApiResponse.success(service.messages(symbol, afterId));
    }

    @PostMapping("/{symbol}/messages")
    public ApiResponse<FinanceMessageView> post(@PathVariable String symbol,
            @AuthenticationPrincipal AuthenticatedMember member, @Valid @RequestBody PostRequest request) {
        return ApiResponse.success(service.post(symbol, member.memberId(), request.requestId(), request.content()));
    }

    public record PostRequest(@NotNull UUID requestId, @NotBlank String content) {}
}
