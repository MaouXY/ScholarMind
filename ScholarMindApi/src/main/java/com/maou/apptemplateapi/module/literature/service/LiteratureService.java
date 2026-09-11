package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.result.PageResult;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import com.maou.apptemplateapi.module.literature.dto.LiteratureRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureReviewRequest;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureService {

    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String BIZ_TEMP = "TEMP";
    private static final String BIZ_LITERATURE_FILE = "LITERATURE_FILE";

    private final LiteratureMapper literatureMapper;
    private final LiteratureCategoryMapper categoryMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final LiteratureCategoryService categoryService;

    public PageResult<LiteratureResponse> list(long page, long size, String keyword, Long categoryId, String status, String visibility) {
        CurrentUser currentUser = CurrentUserProvider.require();
        boolean admin = currentUser.getRole() == UserRole.ADMIN;
        Page<Literature> result = literatureMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<Literature>()
                        .eq(Literature::getDeleted, 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(Literature::getTitle, keyword)
                                .or()
                                .like(Literature::getAuthor, keyword)
                                .or()
                                .like(Literature::getKeywords, keyword)
                                .or()
                                .like(Literature::getAbstractText, keyword))
                        .eq(categoryId != null, Literature::getCategoryId, categoryId)
                        .eq(StringUtils.hasText(status), Literature::getStatus, status)
                        .eq(StringUtils.hasText(visibility), Literature::getVisibility, visibility)
                        .and(!admin, wrapper -> wrapper
                                .eq(Literature::getUploaderId, currentUser.getId())
                                .or(nested -> nested.eq(Literature::getVisibility, VISIBILITY_PUBLIC)
                                        .eq(Literature::getStatus, STATUS_PUBLISHED)))
                        .orderByDesc(Literature::getUpdatedAt)
                        .orderByDesc(Literature::getId));
        Map<Long, String> categoryNames = categoryNames(result);
        return PageResult.of(result.getRecords().stream().map(item -> toResponse(item, categoryNames.get(item.getCategoryId()))).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    public PageResult<LiteratureResponse> pending(long page, long size) {
        requireAdmin("admin-list-pending-literatures");
        Page<Literature> result = literatureMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<Literature>()
                        .eq(Literature::getDeleted, 0)
                        .eq(Literature::getStatus, STATUS_PENDING_REVIEW)
                        .orderByAsc(Literature::getUpdatedAt)
                        .orderByAsc(Literature::getId));
        Map<Long, String> categoryNames = categoryNames(result);
        return PageResult.of(result.getRecords().stream().map(item -> toResponse(item, categoryNames.get(item.getCategoryId()))).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    public LiteratureResponse detail(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = requireLiterature(id, "literature-detail");
        ensureReadable(literature, currentUser, "literature-detail");
        String categoryName = categoryName(literature.getCategoryId());
        return toResponse(literature, categoryName);
    }

    @Transactional
    public LiteratureResponse create(LiteratureRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() == UserRole.ADMIN) {
            log.warn("literature create rejected, scenario=literature-create, reason=admin-not-uploader, userId={}", currentUser.getId());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Literature literature = new Literature();
        apply(literature, request, currentUser, "literature-create");
        literature.setUploaderId(currentUser.getId());
        literature.setStatus(resolveInitialStatus(literature.getVisibility()));
        literature.setReadCount(0);
        literature.setDownloadCount(0);
        literatureMapper.insert(literature);
        bindFileIfNecessary(literature, request.fileId(), currentUser, "literature-create");
        log.info("literature created, scenario=literature-create, literatureId={}, uploaderId={}, visibility={}, status={}, fileId={}",
                literature.getId(), currentUser.getId(), literature.getVisibility(), literature.getStatus(), literature.getFileId());
        return detail(literature.getId());
    }

    @Transactional
    public LiteratureResponse update(Long id, LiteratureRequest request) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = requireLiterature(id, "literature-update");
        ensureOwnerOrAdmin(literature, currentUser, "literature-update");
        Long oldFileId = literature.getFileId();
        apply(literature, request, currentUser, "literature-update");
        if (VISIBILITY_PUBLIC.equals(literature.getVisibility())
                && currentUser.getRole() != UserRole.ADMIN
                && !STATUS_PUBLISHED.equals(literature.getStatus())) {
            literature.setStatus(STATUS_PENDING_REVIEW);
        }
        literatureMapper.updateById(literature);
        if (!Objects.equals(oldFileId, request.fileId())) {
            bindFileIfNecessary(literature, request.fileId(), currentUser, "literature-update");
        }
        log.info("literature updated, scenario=literature-update, literatureId={}, operatorId={}, visibility={}, status={}, fileId={}",
                id, currentUser.getId(), literature.getVisibility(), literature.getStatus(), literature.getFileId());
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = requireLiterature(id, "literature-delete");
        ensureOwnerOrAdmin(literature, currentUser, "literature-delete");
        literatureMapper.deleteById(id);
        log.info("literature deleted, scenario=literature-delete, literatureId={}, operatorId={}", id, currentUser.getId());
    }

    @Transactional
    public LiteratureResponse submit(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = requireLiterature(id, "literature-submit");
        ensureOwner(literature, currentUser, "literature-submit");
        if (!VISIBILITY_PUBLIC.equals(literature.getVisibility())) {
            log.warn("literature submit rejected, scenario=literature-submit, reason=not-public, literatureId={}, uploaderId={}, visibility={}",
                    id, currentUser.getId(), literature.getVisibility());
            throw new BusinessException(ErrorCode.LITERATURE_STATUS_INVALID);
        }
        literature.setStatus(STATUS_PENDING_REVIEW);
        literature.setRejectReason(null);
        literatureMapper.updateById(literature);
        log.info("literature submitted, scenario=literature-submit, literatureId={}, uploaderId={}", id, currentUser.getId());
        return detail(id);
    }

    @Transactional
    public LiteratureResponse approve(Long id) {
        CurrentUser admin = requireAdmin("admin-approve-literature");
        Literature literature = requireLiterature(id, "admin-approve-literature");
        if (!VISIBILITY_PUBLIC.equals(literature.getVisibility())) {
            log.warn("literature approve rejected, scenario=admin-approve-literature, reason=not-public, literatureId={}, adminId={}, visibility={}",
                    id, admin.getId(), literature.getVisibility());
            throw new BusinessException(ErrorCode.LITERATURE_STATUS_INVALID);
        }
        literature.setStatus(STATUS_PUBLISHED);
        literature.setReviewedBy(admin.getId());
        literature.setReviewedAt(LocalDateTime.now());
        literature.setRejectReason(null);
        literatureMapper.updateById(literature);
        log.info("literature approved, scenario=admin-approve-literature, literatureId={}, adminId={}", id, admin.getId());
        return toResponse(literatureMapper.selectById(id), categoryName(literature.getCategoryId()));
    }

    @Transactional
    public LiteratureResponse reject(Long id, LiteratureReviewRequest request) {
        CurrentUser admin = requireAdmin("admin-reject-literature");
        Literature literature = requireLiterature(id, "admin-reject-literature");
        if (!STATUS_PENDING_REVIEW.equals(literature.getStatus())) {
            log.warn("literature reject rejected, scenario=admin-reject-literature, reason=status-invalid, literatureId={}, adminId={}, status={}",
                    id, admin.getId(), literature.getStatus());
            throw new BusinessException(ErrorCode.LITERATURE_STATUS_INVALID);
        }
        literature.setStatus(STATUS_REJECTED);
        literature.setReviewedBy(admin.getId());
        literature.setReviewedAt(LocalDateTime.now());
        literature.setRejectReason(request.reason());
        literatureMapper.updateById(literature);
        log.info("literature rejected, scenario=admin-reject-literature, literatureId={}, adminId={}, reason={}", id, admin.getId(), request.reason());
        return toResponse(literatureMapper.selectById(id), categoryName(literature.getCategoryId()));
    }

    @Transactional
    public LiteratureResponse increaseReadCount(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = requireLiterature(id, "literature-read");
        ensureReadable(literature, currentUser, "literature-read");
        literature.setReadCount((literature.getReadCount() == null ? 0 : literature.getReadCount()) + 1);
        literatureMapper.updateById(literature);
        log.info("literature read counted, scenario=literature-read, literatureId={}, userId={}, readCount={}",
                id, currentUser.getId(), literature.getReadCount());
        return toResponse(literatureMapper.selectById(id), categoryName(literature.getCategoryId()));
    }

    private void apply(Literature literature, LiteratureRequest request, CurrentUser currentUser, String scenario) {
        if (request.categoryId() != null) {
            LiteratureCategory category = categoryService.requireCategory(request.categoryId(), scenario);
            if (category.getStatus() == null || category.getStatus() != 1) {
                log.warn("literature category invalid, scenario={}, literatureId={}, categoryId={}, status={}",
                        scenario, literature.getId(), category.getId(), category.getStatus());
                throw new BusinessException(ErrorCode.LITERATURE_CATEGORY_NOT_FOUND);
            }
        }
        literature.setTitle(request.title());
        literature.setAuthor(request.author());
        literature.setSource(request.source());
        literature.setPublishYear(request.publishYear());
        literature.setLanguage(StringUtils.hasText(request.language()) ? request.language() : "zh-CN");
        literature.setCategoryId(request.categoryId());
        literature.setVisibility(StringUtils.hasText(request.visibility()) ? request.visibility() : VISIBILITY_PRIVATE);
        literature.setAbstractText(request.abstractText());
        literature.setKeywords(request.keywords());
        if (request.fileId() != null) {
            FileMetadata file = requireBindableFile(request.fileId(), currentUser, scenario);
            literature.setFileId(file.getId());
            literature.setFileUrl(file.getPublicUrl());
            literature.setFileType(file.getContentType());
            literature.setFileSize(file.getSizeBytes());
        } else {
            literature.setFileId(null);
            literature.setFileUrl(null);
            literature.setFileType(null);
            literature.setFileSize(null);
        }
    }

    private String resolveInitialStatus(String visibility) {
        return VISIBILITY_PUBLIC.equals(visibility) ? STATUS_PENDING_REVIEW : STATUS_DRAFT;
    }

    private FileMetadata requireBindableFile(Long fileId, CurrentUser currentUser, String scenario) {
        FileMetadata file = fileMetadataMapper.selectById(fileId);
        if (file == null || file.getDeleted() != 0) {
            log.warn("literature file not found, scenario={}, fileId={}, userId={}", scenario, fileId, currentUser.getId());
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!currentUser.getId().equals(file.getUploaderId())) {
            log.warn("literature file denied, scenario={}, fileId={}, uploaderId={}, currentUserId={}",
                    scenario, fileId, file.getUploaderId(), currentUser.getId());
            throw new BusinessException(ErrorCode.FILE_ACCESS_DENIED);
        }
        if (!BIZ_TEMP.equals(file.getBizType()) && !BIZ_LITERATURE_FILE.equals(file.getBizType())) {
            log.warn("literature file invalid, scenario={}, fileId={}, bizType={}, bizId={}",
                    scenario, fileId, file.getBizType(), file.getBizId());
            throw new BusinessException(ErrorCode.LITERATURE_FILE_INVALID);
        }
        return file;
    }

    private void bindFileIfNecessary(Literature literature, Long fileId, CurrentUser currentUser, String scenario) {
        if (fileId == null) {
            return;
        }
        FileMetadata file = requireBindableFile(fileId, currentUser, scenario);
        file.setBizType(BIZ_LITERATURE_FILE);
        file.setBizId(literature.getId());
        fileMetadataMapper.updateById(file);
    }

    public Literature requireReadableLiterature(Long id, CurrentUser currentUser, String scenario) {
        Literature literature = requireLiterature(id, scenario);
        ensureReadable(literature, currentUser, scenario);
        return literature;
    }

    Literature requireLiterature(Long id, String scenario) {
        Literature literature = literatureMapper.selectById(id);
        if (literature == null || literature.getDeleted() != 0) {
            log.warn("literature not found, scenario={}, literatureId={}", scenario, id);
            throw new BusinessException(ErrorCode.LITERATURE_NOT_FOUND);
        }
        return literature;
    }

    private void ensureReadable(Literature literature, CurrentUser currentUser, String scenario) {
        if (currentUser.getRole() == UserRole.ADMIN || currentUser.getId().equals(literature.getUploaderId())) {
            return;
        }
        if (VISIBILITY_PUBLIC.equals(literature.getVisibility()) && STATUS_PUBLISHED.equals(literature.getStatus())) {
            return;
        }
        log.warn("literature access denied, scenario={}, literatureId={}, userId={}, roleCode={}, uploaderId={}, visibility={}, status={}",
                scenario, literature.getId(), currentUser.getId(), currentUser.getRoleCode(), literature.getUploaderId(),
                literature.getVisibility(), literature.getStatus());
        throw new BusinessException(ErrorCode.LITERATURE_ACCESS_DENIED);
    }

    private void ensureOwner(Literature literature, CurrentUser currentUser, String scenario) {
        if (!currentUser.getId().equals(literature.getUploaderId())) {
            log.warn("literature owner denied, scenario={}, literatureId={}, userId={}, uploaderId={}",
                    scenario, literature.getId(), currentUser.getId(), literature.getUploaderId());
            throw new BusinessException(ErrorCode.LITERATURE_ACCESS_DENIED);
        }
    }

    private void ensureOwnerOrAdmin(Literature literature, CurrentUser currentUser, String scenario) {
        if (currentUser.getRole() == UserRole.ADMIN || currentUser.getId().equals(literature.getUploaderId())) {
            return;
        }
        log.warn("literature operator denied, scenario={}, literatureId={}, userId={}, roleCode={}, uploaderId={}",
                scenario, literature.getId(), currentUser.getId(), currentUser.getRoleCode(), literature.getUploaderId());
        throw new BusinessException(ErrorCode.LITERATURE_ACCESS_DENIED);
    }

    private CurrentUser requireAdmin(String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("literature role denied, scenario={}, userId={}, roleCode={}", scenario, currentUser.getId(), currentUser.getRoleCode());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return currentUser;
    }

    private Map<Long, String> categoryNames(Page<Literature> result) {
        var categoryIds = result.getRecords().stream()
                .map(Literature::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(LiteratureCategory::getId, LiteratureCategory::getName));
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        LiteratureCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    LiteratureResponse toResponse(Literature literature, String categoryName) {
        return new LiteratureResponse(literature.getId(), literature.getTitle(), literature.getAuthor(), literature.getSource(),
                literature.getPublishYear(), literature.getLanguage(), literature.getCategoryId(), categoryName,
                literature.getUploaderId(), literature.getVisibility(), literature.getStatus(), literature.getFileId(),
                literature.getFileUrl(), literature.getFileType(), literature.getFileSize(), literature.getAbstractText(),
                literature.getAiSummary(), literature.getKeywords(), literature.getReadCount(), literature.getDownloadCount(),
                literature.getReviewedBy(), literature.getReviewedAt(), literature.getRejectReason(), literature.getCreatedAt(), literature.getUpdatedAt());
    }
}
