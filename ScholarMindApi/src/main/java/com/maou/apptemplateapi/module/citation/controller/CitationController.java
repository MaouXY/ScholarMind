package com.maou.apptemplateapi.module.citation.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.citation.dto.CitationRecordResponse;
import com.maou.apptemplateapi.module.citation.dto.CitationSaveRequest;
import com.maou.apptemplateapi.module.citation.enums.CitationStyle;
import com.maou.apptemplateapi.module.citation.service.CitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/citations")
public class CitationController {

    private final CitationService citationService;

    @GetMapping
    public ApiResponse<PageResult<CitationRecordResponse>> list(
            @RequestParam(required = false) Long literatureId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(citationService.list(page, size, literatureId));
    }

    @PostMapping
    public ApiResponse<CitationRecordResponse> save(@Valid @RequestBody CitationSaveRequest request) {
        return ApiResponse.success(citationService.save(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        citationService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 导出参考文献列表：直接返回 text/plain 附件，前端可直接下载为文件。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "GB_T_7714") String style) {
        CitationStyle citationStyle = CitationStyle.fromCode(style);
        byte[] content = citationService.exportText(citationStyle.name()).getBytes(StandardCharsets.UTF_8);
        String filename = "references-" + citationStyle.name() + ".txt";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(content);
    }
}
