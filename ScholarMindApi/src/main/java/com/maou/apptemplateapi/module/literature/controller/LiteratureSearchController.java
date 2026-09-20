package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureSearchRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureSearchResponse;
import com.maou.apptemplateapi.module.literature.dto.RetrievalCompareResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class LiteratureSearchController {

    private final LiteratureAiService literatureAiService;

    /** 语义检索（已升级为混合检索：BM25 稀疏 + 向量稠密 + RRF 融合）。 */
    @PostMapping("/semantic")
    public ApiResponse<LiteratureSearchResponse> semantic(@Valid @RequestBody LiteratureSearchRequest request) {
        return ApiResponse.success(literatureAiService.semanticSearch(request));
    }

    /** 混合检索的显式入口，与 /semantic 行为一致，命名更贴合实现。 */
    @PostMapping("/hybrid")
    public ApiResponse<LiteratureSearchResponse> hybrid(@Valid @RequestBody LiteratureSearchRequest request) {
        return ApiResponse.success(literatureAiService.semanticSearch(request));
    }

    /** 自然语言检索：先解析意图，再执行混合检索。 */
    @PostMapping("/natural-language")
    public ApiResponse<LiteratureSearchResponse> naturalLanguage(@Valid @RequestBody LiteratureSearchRequest request) {
        return ApiResponse.success(literatureAiService.naturalLanguageSearch(request));
    }

    /** 检索消融对比：同时返回稀疏、稠密与 RRF 融合三路排序结果。 */
    @PostMapping("/retrieval-compare")
    public ApiResponse<RetrievalCompareResponse> retrievalCompare(@Valid @RequestBody LiteratureSearchRequest request) {
        return ApiResponse.success(literatureAiService.compareRetrieval(request));
    }
}
