package com.fitmeet.instruments;

import com.fitmeet.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstrumentController {
    private final InstrumentCatalog catalog;

    public InstrumentController(InstrumentCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/api/v1/instruments")
    public ApiResponse<List<InstrumentCatalog.Instrument>> list() {
        return ApiResponse.success(catalog.list());
    }
}
