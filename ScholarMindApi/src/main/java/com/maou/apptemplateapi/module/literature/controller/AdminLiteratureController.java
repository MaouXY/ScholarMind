package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端文献接口。
 * <p>客户 2026-11-15 口径调整后，文献上传统一走 {@code POST /api/v1/literatures}
 * （服务端校验管理员身份，上传即发布），此处只保留删除能力。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/literatures")
public class AdminLiteratureController {

    private final LiteratureService literatureService;

    /** 管理端删除文献：级联清理分块、收藏、阅读历史、笔记、引用、问答、附件与向量。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        literatureService.adminDelete(id);
        return ApiResponse.success();
    }
}
