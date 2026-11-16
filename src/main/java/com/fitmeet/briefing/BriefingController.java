package com.fitmeet.briefing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BriefingController {
    private final BriefingGateway gateway;

    public BriefingController(BriefingGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/api/v1/briefings/{symbol}")
    public ResponseEntity<ObjectNode> lookup(
            @PathVariable String symbol, @RequestParam(defaultValue = "7d") String window
    ) {
        if (!"7d".equals(window) || !BriefingGateway.SYMBOLS.contains(symbol)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gateway.lookup(symbol));
    }
}
