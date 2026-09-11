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
import com.maou.apptemplateapi.module.literature.entity.LiteratureFavorite;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureFavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureFavoriteService {

    private final LiteratureFavoriteMapper favoriteMapper;
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
        return toResponse(favoriteMapper.selectById(favorite.getId()));
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
        return PageResult.of(result.getRecords().stream().map(this::toResponse).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
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

    private FavoriteResponse toResponse(LiteratureFavorite favorite) {
        return new FavoriteResponse(favorite.getId(), favorite.getLiteratureId(), favorite.getCreatedAt());
    }
}
