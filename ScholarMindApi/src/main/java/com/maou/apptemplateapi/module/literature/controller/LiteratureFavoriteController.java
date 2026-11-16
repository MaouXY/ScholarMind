package com.maou.apptemplateapi.module.literature.controller;

import com.maou.apptemplateapi.common.result.ApiResponse;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.module.literature.dto.FavoriteFolderRequest;
import com.maou.apptemplateapi.module.literature.dto.FavoriteFolderResponse;
import com.maou.apptemplateapi.module.literature.dto.FavoriteResponse;
import com.maou.apptemplateapi.module.literature.service.LiteratureFavoriteFolderService;
import com.maou.apptemplateapi.module.literature.service.LiteratureFavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 我的收藏与收藏夹。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/favorites")
public class LiteratureFavoriteController {

    private final LiteratureFavoriteService literatureFavoriteService;
    private final LiteratureFavoriteFolderService folderService;

    /** 收藏文献；folderId 可选，省略时落到默认收藏夹。 */
    @PostMapping("/{literatureId}")
    public ApiResponse<FavoriteResponse> favorite(@PathVariable Long literatureId,
                                                  @RequestParam(required = false) Long folderId) {
        return ApiResponse.success(literatureFavoriteService.favorite(literatureId, folderId));
    }

    @DeleteMapping("/{literatureId}")
    public ApiResponse<Void> unfavorite(@PathVariable Long literatureId) {
        literatureFavoriteService.unfavorite(literatureId);
        return ApiResponse.success();
    }

    /** 收藏列表；folderId 传 0 只看未分类，省略看全部。 */
    @GetMapping
    public ApiResponse<PageResult<FavoriteResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long folderId) {
        return ApiResponse.success(literatureFavoriteService.list(page, size, folderId));
    }

    /** 我的收藏夹（含各夹收藏数）。 */
    @GetMapping("/folders")
    public ApiResponse<List<FavoriteFolderResponse>> folders() {
        return ApiResponse.success(folderService.list());
    }

    @PostMapping("/folders")
    public ApiResponse<FavoriteFolderResponse> createFolder(@Valid @RequestBody FavoriteFolderRequest request) {
        return ApiResponse.success(folderService.create(request));
    }

    @PutMapping("/folders/{id}")
    public ApiResponse<FavoriteFolderResponse> updateFolder(@PathVariable Long id,
                                                            @Valid @RequestBody FavoriteFolderRequest request) {
        return ApiResponse.success(folderService.update(id, request));
    }

    /** 删除收藏夹：夹内文献退回未分类，收藏本身保留。 */
    @DeleteMapping("/folders/{id}")
    public ApiResponse<Void> deleteFolder(@PathVariable Long id) {
        folderService.delete(id);
        return ApiResponse.success();
    }

    /** 把某篇已收藏的文献移动到另一个收藏夹；folderId 传 0 表示移入未分类。 */
    @PutMapping("/{literatureId}/folder")
    public ApiResponse<Void> moveToFolder(@PathVariable Long literatureId,
                                          @RequestParam(required = false) Long folderId) {
        folderService.moveLiterature(literatureId, folderId);
        return ApiResponse.success();
    }
}
