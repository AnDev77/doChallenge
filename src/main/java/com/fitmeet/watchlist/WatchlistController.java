package com.fitmeet.watchlist;

import com.fitmeet.auth.domain.AuthenticatedMember;
import com.fitmeet.common.response.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {
    private final WatchlistService service;

    public WatchlistController(WatchlistService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<String>> list(@AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(service.list(member.memberId()));
    }

    @PutMapping("/{symbol}")
    public ApiResponse<List<String>> add(@PathVariable String symbol,
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(service.add(member.memberId(), symbol));
    }

    @DeleteMapping("/{symbol}")
    public ApiResponse<List<String>> remove(@PathVariable String symbol,
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(service.remove(member.memberId(), symbol));
    }
}
