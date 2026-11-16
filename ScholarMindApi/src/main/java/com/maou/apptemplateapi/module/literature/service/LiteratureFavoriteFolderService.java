package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.FavoriteFolderRequest;
import com.maou.apptemplateapi.module.literature.dto.FavoriteFolderResponse;
import com.maou.apptemplateapi.module.literature.entity.LiteratureFavorite;
import com.maou.apptemplateapi.module.literature.entity.LiteratureFavoriteFolder;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureFavoriteFolderMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureFavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 收藏夹服务。
 * <p>单归属：一篇文献只属于一个收藏夹，{@code folder_id} 为空表示「未分类」。
 * 删除收藏夹时夹内文献退回「未分类」，不连带删除收藏记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureFavoriteFolderService {

    /** 未分类的哨兵值：前端传 0 或空表示不指定收藏夹 */
    private static final Long UNCLASSIFIED = 0L;

    private final LiteratureFavoriteFolderMapper folderMapper;
    private final LiteratureFavoriteMapper favoriteMapper;

    /** 列出当前用户的收藏夹，附带各夹的收藏数量。 */
    public List<FavoriteFolderResponse> list() {
        CurrentUser currentUser = CurrentUserProvider.require();
        List<LiteratureFavoriteFolder> folders = folderMapper.selectList(
                new LambdaQueryWrapper<LiteratureFavoriteFolder>()
                        .eq(LiteratureFavoriteFolder::getUserId, currentUser.getId())
                        .eq(LiteratureFavoriteFolder::getDeleted, 0)
                        .orderByAsc(LiteratureFavoriteFolder::getSort)
                        .orderByAsc(LiteratureFavoriteFolder::getId));
        Map<Long, Long> counts = countByFolder(currentUser.getId());
        return folders.stream()
                .map(folder -> toResponse(folder, counts.getOrDefault(folder.getId(), 0L)))
                .toList();
    }

    @Transactional
    public FavoriteFolderResponse create(FavoriteFolderRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        String name = request.name().trim();
        ensureNameAvailable(currentUser.getId(), name, null, "favorite-folder-create");
        LiteratureFavoriteFolder folder = new LiteratureFavoriteFolder();
        folder.setUserId(currentUser.getId());
        folder.setName(name);
        folder.setDescription(request.description());
        folder.setSort(request.sort() == null ? 0 : request.sort());
        try {
            folderMapper.insert(folder);
        } catch (DuplicateKeyException exception) {
            log.warn("favorite folder duplicated, scenario=favorite-folder-create, userId={}, name={}",
                    currentUser.getId(), name);
            throw new BusinessException(ErrorCode.FAVORITE_FOLDER_DUPLICATED);
        }
        log.info("favorite folder created, scenario=favorite-folder-create, userId={}, folderId={}, name={}",
                currentUser.getId(), folder.getId(), name);
        return toResponse(folder, 0L);
    }

    @Transactional
    public FavoriteFolderResponse update(Long id, FavoriteFolderRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        LiteratureFavoriteFolder folder = requireOwnFolder(id, currentUser.getId(), "favorite-folder-update");
        String name = request.name().trim();
        ensureNameAvailable(currentUser.getId(), name, id, "favorite-folder-update");
        folder.setName(name);
        folder.setDescription(request.description());
        if (request.sort() != null) {
            folder.setSort(request.sort());
        }
        try {
            folderMapper.updateById(folder);
        } catch (DuplicateKeyException exception) {
            log.warn("favorite folder duplicated, scenario=favorite-folder-update, userId={}, folderId={}, name={}",
                    currentUser.getId(), id, name);
            throw new BusinessException(ErrorCode.FAVORITE_FOLDER_DUPLICATED);
        }
        log.info("favorite folder updated, scenario=favorite-folder-update, userId={}, folderId={}, name={}",
                currentUser.getId(), id, name);
        return toResponse(folder, countByFolder(currentUser.getId()).getOrDefault(id, 0L));
    }

    /** 删除收藏夹：夹内文献退回未分类，收藏本身保留。 */
    @Transactional
    public void delete(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        LiteratureFavoriteFolder folder = requireOwnFolder(id, currentUser.getId(), "favorite-folder-delete");
        // 同样必须用 UpdateWrapper：把 folder_id 显式置 NULL（updateById 会忽略 null 字段）。
        int detached = favoriteMapper.update(null, new LambdaUpdateWrapper<LiteratureFavorite>()
                .eq(LiteratureFavorite::getUserId, currentUser.getId())
                .eq(LiteratureFavorite::getFolderId, id)
                .set(LiteratureFavorite::getFolderId, null));
        folderMapper.deleteById(id);
        log.info("favorite folder deleted, scenario=favorite-folder-delete, userId={}, folderId={}, detachedFavorites={}",
                currentUser.getId(), folder.getId(), detached);
    }

    /** 把收藏移动到指定收藏夹；folderId 为空或 0 表示移入未分类。 */
    @Transactional
    public void moveLiterature(Long literatureId, Long folderId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Long targetFolderId = folderId == null || UNCLASSIFIED.equals(folderId) ? null : folderId;
        if (targetFolderId != null) {
            requireOwnFolder(targetFolderId, currentUser.getId(), "favorite-folder-move");
        }
        LiteratureFavorite favorite = favoriteMapper.selectOne(new LambdaQueryWrapper<LiteratureFavorite>()
                .eq(LiteratureFavorite::getUserId, currentUser.getId())
                .eq(LiteratureFavorite::getLiteratureId, literatureId)
                .eq(LiteratureFavorite::getDeleted, 0)
                .last("limit 1"));
        if (favorite == null) {
            log.warn("favorite not found for move, scenario=favorite-folder-move, userId={}, literatureId={}",
                    currentUser.getId(), literatureId);
            throw new BusinessException(ErrorCode.LITERATURE_FAVORITE_NOT_FOUND);
        }
        // 用 UpdateWrapper 而不是 updateById：MyBatis-Plus 默认忽略 null 字段，
        // 而「移入未分类」正是要把 folder_id 写回 NULL。
        favoriteMapper.update(null, new LambdaUpdateWrapper<LiteratureFavorite>()
                .eq(LiteratureFavorite::getId, favorite.getId())
                .set(LiteratureFavorite::getFolderId, targetFolderId));
        log.info("favorite moved, scenario=favorite-folder-move, userId={}, literatureId={}, folderId={}",
                currentUser.getId(), literatureId, targetFolderId);
    }

    /**
     * 为新收藏决定归属：显式指定则校验归属，未指定则落到「默认收藏夹」，
     * 该用户还没有任何收藏夹时自动创建「默认收藏夹」。
     */
    @Transactional
    public Long resolveFolderForNewFavorite(Long userId, Long folderId) {
        if (folderId != null && !UNCLASSIFIED.equals(folderId)) {
            return requireOwnFolder(folderId, userId, "literature-favorite-create").getId();
        }
        LiteratureFavoriteFolder defaultFolder = folderMapper.selectOne(
                new LambdaQueryWrapper<LiteratureFavoriteFolder>()
                        .eq(LiteratureFavoriteFolder::getUserId, userId)
                        .eq(LiteratureFavoriteFolder::getDeleted, 0)
                        .orderByAsc(LiteratureFavoriteFolder::getSort)
                        .orderByAsc(LiteratureFavoriteFolder::getId)
                        .last("limit 1"));
        if (defaultFolder != null) {
            return defaultFolder.getId();
        }
        LiteratureFavoriteFolder created = new LiteratureFavoriteFolder();
        created.setUserId(userId);
        created.setName("默认收藏夹");
        created.setDescription("首次收藏时自动创建");
        created.setSort(0);
        folderMapper.insert(created);
        log.info("default favorite folder created, scenario=literature-favorite-create, userId={}, folderId={}",
                userId, created.getId());
        return created.getId();
    }

    /** 校验收藏夹归属当前用户，返回实体。 */
    LiteratureFavoriteFolder requireOwnFolder(Long id, Long userId, String scenario) {
        LiteratureFavoriteFolder folder = folderMapper.selectById(id);
        if (folder == null || folder.getDeleted() != 0) {
            log.warn("favorite folder not found, scenario={}, folderId={}", scenario, id);
            throw new BusinessException(ErrorCode.FAVORITE_FOLDER_NOT_FOUND);
        }
        if (!Objects.equals(folder.getUserId(), userId)) {
            log.warn("favorite folder access denied, scenario={}, folderId={}, ownerId={}, userId={}",
                    scenario, id, folder.getUserId(), userId);
            throw new BusinessException(ErrorCode.FAVORITE_FOLDER_NOT_FOUND);
        }
        return folder;
    }

    private void ensureNameAvailable(Long userId, String name, Long excludeId, String scenario) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "folder name must not be blank");
        }
        long duplicated = folderMapper.selectCount(new LambdaQueryWrapper<LiteratureFavoriteFolder>()
                .eq(LiteratureFavoriteFolder::getUserId, userId)
                .eq(LiteratureFavoriteFolder::getName, name)
                .eq(LiteratureFavoriteFolder::getDeleted, 0)
                .ne(excludeId != null, LiteratureFavoriteFolder::getId, excludeId));
        if (duplicated > 0) {
            log.warn("favorite folder name duplicated, scenario={}, userId={}, name={}", scenario, userId, name);
            throw new BusinessException(ErrorCode.FAVORITE_FOLDER_DUPLICATED);
        }
    }

    /** 统计当前用户各收藏夹的收藏数量（未分类不计入任何夹）。 */
    private Map<Long, Long> countByFolder(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<LiteratureFavorite>()
                        .eq(LiteratureFavorite::getUserId, userId)
                        .eq(LiteratureFavorite::getDeleted, 0)
                        .isNotNull(LiteratureFavorite::getFolderId))
                .stream()
                .collect(Collectors.groupingBy(LiteratureFavorite::getFolderId, Collectors.counting()));
    }

    private FavoriteFolderResponse toResponse(LiteratureFavoriteFolder folder, Long favoriteCount) {
        return new FavoriteFolderResponse(folder.getId(), folder.getName(), folder.getDescription(),
                folder.getSort(), favoriteCount.intValue(), folder.getCreatedAt());
    }
}
