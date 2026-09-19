package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.FavoriteResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.entity.LiteratureFavorite;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureFavoriteMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureFavoriteService {

    private static final String UNKNOWN_TITLE = "文献已删除或不可见";

    private final LiteratureFavoriteMapper favoriteMapper;
    private final LiteratureMapper literatureMapper;
    private final LiteratureCategoryMapper categoryMapper;
    private final LiteratureService literatureService;

    @Transactional
    public FavoriteResponse favorite(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-favorite-create");
        LiteratureFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<LiteratureFavorite>()
                .eq(LiteratureFavorite::getUserId, currentUser.getId())
                .eq(LiteratureFavorite::getLiteratureId, literatureId)
                .eq(LiteratureFavorite::getDeleted, 0)
                .last("limit 1"));
        if (existing != null) {
            log.warn("literature favorite duplicated, scenario=literature-favorite-create, literatureId={}, userId={}",
                    literatureId, currentUser.getId());
            throw new BusinessException(ErrorCode.LITERATURE_FAVORITE_DUPLICATED);
        }
        LiteratureFavorite favorite = new LiteratureFavorite();
        favorite.setUserId(currentUser.getId());
        favorite.setLiteratureId(literature.getId());
        favoriteMapper.insert(favorite);
        log.info("literature favorited, scenario=literature-favorite-create, literatureId={}, userId={}, favoriteId={}",
                literatureId, currentUser.getId(), favorite.getId());
        return toResponse(favoriteMapper.selectById(favorite.getId()), literature, categoryName(literature.getCategoryId()));
    }

    @Transactional
    public void unfavorite(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        LiteratureFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<LiteratureFavorite>()
                .eq(LiteratureFavorite::getUserId, currentUser.getId())
                .eq(LiteratureFavorite::getLiteratureId, literatureId)
                .eq(LiteratureFavorite::getDeleted, 0)
                .last("limit 1"));
        if (favorite == null) {
            log.warn("literature favorite not found, scenario=literature-favorite-delete, literatureId={}, userId={}",
                    literatureId, currentUser.getId());
            throw new BusinessException(ErrorCode.LITERATURE_FAVORITE_NOT_FOUND);
        }
        favoriteMapper.deleteById(favorite.getId());
        log.info("literature unfavorited, scenario=literature-favorite-delete, literatureId={}, userId={}, favoriteId={}",
                literatureId, currentUser.getId(), favorite.getId());
    }

    public PageResult<FavoriteResponse> list(long page, long size) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Page<LiteratureFavorite> result = favoriteMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<LiteratureFavorite>()
                        .eq(LiteratureFavorite::getUserId, currentUser.getId())
                        .eq(LiteratureFavorite::getDeleted, 0)
                        .orderByDesc(LiteratureFavorite::getCreatedAt)
                        .orderByDesc(LiteratureFavorite::getId));
        Map<Long, Literature> literatureMap = loadLiteratures(result.getRecords().stream()
                .map(LiteratureFavorite::getLiteratureId)
                .distinct()
                .toList());
        Map<Long, String> categoryMap = loadCategoryNames(literatureMap.values().stream()
                .map(Literature::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        List<FavoriteResponse> items = result.getRecords().stream()
                .map(favorite -> {
                    Literature literature = literatureMap.get(favorite.getLiteratureId());
                    String categoryName = literature == null || literature.getCategoryId() == null
                            ? null
                            : categoryMap.get(literature.getCategoryId());
                    return toResponse(favorite, literature, categoryName);
                })
                .toList();
        return PageResult.of(items, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public boolean isFavorited(Long literatureId, Long userId) {
        if (literatureId == null || userId == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<LiteratureFavorite>()
                .eq(LiteratureFavorite::getUserId, userId)
                .eq(LiteratureFavorite::getLiteratureId, literatureId)
                .eq(LiteratureFavorite::getDeleted, 0)) > 0;
    }

    private Map<Long, Literature> loadLiteratures(List<Long> literatureIds) {
        if (literatureIds.isEmpty()) {
            return Map.of();
        }
        return literatureMapper.selectBatchIds(literatureIds).stream()
                .collect(Collectors.toMap(Literature::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, String> loadCategoryNames(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectList(new LambdaQueryWrapper<LiteratureCategory>()
                        .in(LiteratureCategory::getId, categoryIds)
                        .eq(LiteratureCategory::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(LiteratureCategory::getId, LiteratureCategory::getName, (left, right) -> left));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        LiteratureCategory category = categoryMapper.selectById(categoryId);
        return category == null || category.getDeleted() != 0 ? null : category.getName();
    }

    private FavoriteResponse toResponse(LiteratureFavorite favorite, Literature literature, String categoryName) {
        boolean readable = literature != null && literature.getDeleted() == 0;
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getLiteratureId(),
                readable && literature.getTitle() != null ? literature.getTitle() : UNKNOWN_TITLE,
                readable ? literature.getAuthor() : null,
                categoryName,
                readable ? literature.getStatus() : null,
                readable ? literature.getVisibility() : null,
                readable && literature.getReadCount() != null ? literature.getReadCount() : 0,
                favorite.getCreatedAt());
    }
}
