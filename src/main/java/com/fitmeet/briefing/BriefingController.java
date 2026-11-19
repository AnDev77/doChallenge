package com.fitmeet.briefing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BriefingController {
    private final BriefingGateway gateway;
    private final JavaBriefingPublication javaPublication;
    private final boolean useJava;

    public BriefingController(BriefingGateway gateway, JavaBriefingPublication javaPublication,
                              @Value("${app.briefing.public-provider:python}") String provider) {
        if (!provider.equals("python") && !provider.equals("java")) {
            throw new IllegalArgumentException("Unsupported public briefing provider");
        }
        this.gateway = gateway;
        this.javaPublication = javaPublication;
        this.useJava = provider.equals("java");
    }

    @GetMapping("/api/v1/briefings/{symbol}")
    public ResponseEntity<ObjectNode> lookup(
            @PathVariable String symbol, @RequestParam(defaultValue = "7d") String window
    ) {
        if (!"7d".equals(window) || !BriefingGateway.SYMBOLS.contains(symbol)) {
            return ResponseEntity.badRequest().build();
        }
        ObjectNode result = useJava ? javaPublication.lookup(symbol) : gateway.lookup(symbol);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
