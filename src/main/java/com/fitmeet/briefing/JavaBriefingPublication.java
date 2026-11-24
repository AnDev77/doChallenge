package com.fitmeet.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitmeet.briefing.publication.BriefingDraftService;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** Formats a reviewed, stored Java draft for the existing public web contract. */
@Service
public class JavaBriefingPublication {
    private final BriefingDraftService drafts;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    public JavaBriefingPublication(BriefingDraftService drafts, ObjectMapper mapper) {
        this.drafts = drafts;
        this.mapper = mapper;
    }

    public ObjectNode lookup(String symbol) {
        return lookup(symbol, 7);
    }

    public ObjectNode lookup(String symbol, int windowDays) {
        if (windowDays != 7 && windowDays != 30) throw new IllegalArgumentException("Unsupported window");
        var approved = drafts.latestApproved(symbol);
        if (approved.isEmpty()) return state(symbol, "insufficient_sources");
        var draft = approved.get();
        if (draft.sources().isEmpty() || draft.summary().isBlank() || draft.summary().length() > 1000
                || draft.sources().size() > 8) return state(symbol, "failed");

        var now = clock.instant();
        var cutoff = now.minus(Duration.ofDays(windowDays));
        if (draft.sources().stream().anyMatch(source -> source.acceptedAt().isBefore(cutoff)
                || source.acceptedAt().isAfter(now) || source.fetchedAt().isAfter(now))) {
            return state(symbol, "insufficient_sources");
        }

        var result = state(symbol, "ready");
        result.put("stance", "withheld");
        var summary = result.putArray("summary").addObject();
        summary.put("text", draft.summary());
        var citationIds = summary.putArray("citation_ids");
        var citations = result.putArray("citations");
        for (var source : draft.sources()) {
            URI url;
            try {
                url = URI.create(source.sourceUrl());
            } catch (IllegalArgumentException error) {
                return state(symbol, "failed");
            }
            if (!"https".equals(url.getScheme()) || !"www.sec.gov".equals(url.getHost())
                    || url.getUserInfo() != null || url.getFragment() != null) return state(symbol, "failed");
            String id = Long.toString(source.chunkId());
            citationIds.add(id);
            var citation = citations.addObject();
            citation.put("id", id);
            citation.put("title", "SEC 공시");
            citation.put("url", url.toString());
            citation.put("published_at", source.acceptedAt().toString());
        }
        result.putArray("bullish_factors");
        result.putArray("bearish_factors");
        result.putArray("change_conditions");
        result.put("generated_at", draft.asOf().toString());
        result.put("freshness", draft.sources().stream().anyMatch(source ->
                source.acceptedAt().isBefore(now.minus(Duration.ofDays(7))))
                ? "stale" : "current");
        return result;
    }

    private ObjectNode state(String symbol, String status) {
        return mapper.createObjectNode().put("symbol", symbol).put("status", status);
    }
}
