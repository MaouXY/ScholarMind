package com.maou.apptemplateapi.module.rag.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.admin.dto.LiteratureVectorRebuildResponse;
import com.maou.apptemplateapi.module.admin.service.AdminLiteratureVectorService;
import com.maou.apptemplateapi.module.rag.dto.KnowledgeDocumentRequest;
import com.maou.apptemplateapi.module.rag.dto.KnowledgeDocumentResponse;
import com.maou.apptemplateapi.module.rag.service.RagKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rag")
public class AdminRagController {

    private final RagKnowledgeService ragKnowledgeService;
    private final AdminLiteratureVectorService adminLiteratureVectorService;

    @GetMapping("/documents")
    public ApiResponse<PageResult<KnowledgeDocumentResponse>> documents(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(ragKnowledgeService.listDocuments(page, size));
    }

    @PostMapping("/documents")
    public ApiResponse<KnowledgeDocumentResponse> create(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return ApiResponse.success(ragKnowledgeService.createDocument(request));
    }

    @PutMapping("/documents/{id}")
    public ApiResponse<KnowledgeDocumentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeDocumentRequest request) {
        return ApiResponse.success(ragKnowledgeService.updateDocument(id, request));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ragKnowledgeService.deleteDocument(id);
        return ApiResponse.success();
    }

    @PostMapping("/documents/{id}/rebuild")
    public ApiResponse<Integer> rebuild(@PathVariable Long id) {
        return ApiResponse.success(ragKnowledgeService.rebuildDocumentChunks(id));
    }

    /** 为所有已发布文献的正文分块重建 Milvus 向量（混合检索的稠密召回依赖它）。 */
    @PostMapping("/literature-vectors/rebuild")
    public ApiResponse<LiteratureVectorRebuildResponse> rebuildLiteratureVectors() {
        return ApiResponse.success(adminLiteratureVectorService.rebuildAll());
    }
}
