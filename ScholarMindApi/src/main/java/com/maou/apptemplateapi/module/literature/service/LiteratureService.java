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
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.event.LiteratureIndexRequestedEvent;
import com.maou.apptemplateapi.module.literature.event.LiteratureSummaryRequestedEvent;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文献主数据服务。
 * <p>客户 2026-11-15 口径调整：文献改由管理员统一上传，上传即发布；学生端只读。
 * 因此「可见性 / 状态 / 提交审核 / 审核通过 / 退回」这些流转全部下线，
 * 数据库列保留（检索与统计链路仍按 PUBLISHED 读取），写入时固定为 PUBLIC / PUBLISHED。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureService {

    /** 文献恒为公开可见（列保留，值固定） */
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    /** 文献恒为已发布（列保留，值固定） */
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String BIZ_TEMP = "TEMP";
    private static final String BIZ_LITERATURE_FILE = "LITERATURE_FILE";
    /** 文献类别缺省值：历史数据与未填写时按学术期刊归档 */
    private static final String DEFAULT_LITERATURE_TYPE = "学术期刊";

    private final LiteratureMapper literatureMapper;
    private final LiteratureCategoryMapper categoryMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final LiteratureCategoryService categoryService;
    private final LiteraturePurgeService literaturePurgeService;
    /**
     * 索引重建与摘要生成走事件：{@code LiteratureIndexService} / {@code LiteratureAiService}
     * 依赖本类做可读性校验，直接反向注入会形成构造器循环依赖。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 检索与列表。
     *
     * @param searchField 检索维度：theme（主题/标题）、keywords（关键词）、fulltext（全文）、
     *                    author（作者）、abstract（摘要）、references（参考文献）、source（文献来源）；
     *                    为空时对全部题录字段做全文匹配
     * @param yearFrom    发表年度下限（含），按 publish_date 的年份比较
     * @param yearTo      发表年度上限（含）
     */
    public PageResult<LiteratureResponse> list(long page, long size, String keyword, String searchField,
                                               Long categoryId, String literatureType,
                                               Integer yearFrom, Integer yearTo) {
        LambdaQueryWrapper<Literature> wrapper = new LambdaQueryWrapper<Literature>()
                .eq(Literature::getDeleted, 0)
                .eq(categoryId != null, Literature::getCategoryId, categoryId)
                .eq(StringUtils.hasText(literatureType), Literature::getLiteratureType, literatureType)
                .ge(yearFrom != null, Literature::getPublishDate, yearStart(yearFrom))
                .lt(yearTo != null, Literature::getPublishDate, yearStart(yearTo == null ? null : yearTo + 1));
        if (StringUtils.hasText(keyword)) {
            applyKeywordFilter(wrapper, keyword.trim(), searchField);
        }
        Page<Literature> result = literatureMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                wrapper.orderByDesc(Literature::getPublishDate).orderByDesc(Literature::getId));
        Map<Long, String> categoryNames = categoryNames(result);
        return PageResult.of(result.getRecords().stream().map(item -> toResponse(item, categoryNames.get(item.getCategoryId()))).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 按检索维度拼接 keyword 条件。
     * <p>「全文」维度覆盖标题、摘要、关键词与参考文献——原文正文分块在 literature_chunk 表，
     * LIKE 无法跨表匹配，这里以题录中的可检索文本近似，命中后再由分块提供精确出处。</p>
     */
    private void applyKeywordFilter(LambdaQueryWrapper<Literature> wrapper, String keyword, String searchField) {
        String field = StringUtils.hasText(searchField) ? searchField.trim().toLowerCase() : "";
        switch (field) {
            case "theme" -> wrapper.like(Literature::getTitle, keyword);
            case "keywords" -> wrapper.like(Literature::getKeywords, keyword);
            case "author" -> wrapper.like(Literature::getAuthor, keyword);
            case "abstract" -> wrapper.like(Literature::getAbstractText, keyword);
            case "references" -> wrapper.like(Literature::getReferencesText, keyword);
            case "source" -> wrapper.like(Literature::getSource, keyword);
            // fulltext 与不指定维度都退化为多字段匹配
            default -> wrapper.and(inner -> inner
                    .like(Literature::getTitle, keyword)
                    .or().like(Literature::getAuthor, keyword)
                    .or().like(Literature::getSource, keyword)
                    .or().like(Literature::getKeywords, keyword)
                    .or().like(Literature::getAbstractText, keyword)
                    .or().like(Literature::getReferencesText, keyword));
        }
    }

    private java.time.LocalDate yearStart(Integer year) {
        return year == null ? null : java.time.LocalDate.of(year, 1, 1);
    }

    public LiteratureResponse detail(Long id) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = requireLiterature(id, "literature-detail");
        ensureReadable(literature, currentUser, "literature-detail");
        return toResponse(literature, categoryName(literature.getCategoryId()));
    }

    /**
     * 管理员上传文献：入库即发布，同时触发索引重建与 AI 摘要生成。
     * 原文是可选的附加步骤——没有原文时索引与摘要都基于题录字段生成。
     */
    @Transactional
    public LiteratureResponse create(LiteratureRequest request) {
        CurrentUser admin = requireAdmin("literature-create");
        Literature literature = new Literature();
        apply(literature, request, admin, "literature-create");
        literature.setUploaderId(admin.getId());
        literature.setVisibility(VISIBILITY_PUBLIC);
        literature.setStatus(STATUS_PUBLISHED);
        literature.setReadCount(0);
        literature.setDownloadCount(0);
        literatureMapper.insert(literature);
        bindFileIfNecessary(literature, request.fileId(), admin, "literature-create");
        log.info("literature uploaded, scenario=literature-create, literatureId={}, adminId={}, literatureType={}, "
                        + "categoryId={}, publishDate={}, onlinePublishDate={}, fileId={}",
                literature.getId(), admin.getId(), literature.getLiteratureType(), literature.getCategoryId(),
                literature.getPublishDate(), literature.getOnlinePublishDate(), literature.getFileId());
        // 上传即发布：索引与摘要都在事务提交后自动完成，管理员不必再手动点按钮
        eventPublisher.publishEvent(new LiteratureIndexRequestedEvent(
                literature.getId(), admin.getId(), LiteratureIndexRequestedEvent.TRIGGER_UPLOAD));
        eventPublisher.publishEvent(new LiteratureSummaryRequestedEvent(
                literature.getId(), admin.getId(), admin.getOrganizationId(), LiteratureSummaryRequestedEvent.TRIGGER_PUBLISH));
        return detail(literature.getId());
    }

    @Transactional
    public LiteratureResponse update(Long id, LiteratureRequest request) {
        CurrentUser admin = requireAdmin("literature-update");
        Literature literature = requireLiterature(id, "literature-update");
        Long oldFileId = literature.getFileId();
        apply(literature, request, admin, "literature-update");
        literatureMapper.updateById(literature);
        if (!Objects.equals(oldFileId, request.fileId())) {
            bindFileIfNecessary(literature, request.fileId(), admin, "literature-update");
        }
        log.info("literature updated, scenario=literature-update, literatureId={}, adminId={}, literatureType={}, "
                        + "categoryId={}, publishDate={}, onlinePublishDate={}, fileId={}",
                id, admin.getId(), literature.getLiteratureType(), literature.getCategoryId(),
                literature.getPublishDate(), literature.getOnlinePublishDate(), literature.getFileId());
        if (!Objects.equals(oldFileId, request.fileId())) {
            // 换了原文：检索仍在用旧正文，题录也可能变了，索引与摘要一并重建
            eventPublisher.publishEvent(new LiteratureIndexRequestedEvent(
                    id, admin.getId(), LiteratureIndexRequestedEvent.TRIGGER_UPLOAD));
            eventPublisher.publishEvent(new LiteratureSummaryRequestedEvent(
                    id, admin.getId(), admin.getOrganizationId(), LiteratureSummaryRequestedEvent.TRIGGER_PUBLISH));
        }
        return detail(id);
    }

    /** 管理端删除文献：不限制归属，级联清理关联数据后再逻辑删除文献主表。 */
    @Transactional
    public void adminDelete(Long id) {
        CurrentUser admin = requireAdmin("admin-delete-literature");
        Literature literature = requireLiterature(id, "admin-delete-literature");
        literaturePurgeService.purge(literature, "admin-delete-literature");
        literatureMapper.deleteById(id);
        log.info("literature deleted by admin, scenario=admin-delete-literature, literatureId={}, adminId={}, uploaderId={}",
                id, admin.getId(), literature.getUploaderId());
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

    private void apply(Literature literature, LiteratureRequest request, CurrentUser operator, String scenario) {
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
        literature.setLiteratureType(StringUtils.hasText(request.literatureType())
                ? request.literatureType() : DEFAULT_LITERATURE_TYPE);
        literature.setCategoryId(request.categoryId());
        literature.setPublishDate(request.publishDate());
        literature.setOnlinePublishDate(request.onlinePublishDate());
        literature.setLanguage(StringUtils.hasText(request.language()) ? request.language() : "zh-CN");
        literature.setAbstractText(request.abstractText());
        literature.setReferencesText(request.referencesText());
        literature.setKeywords(request.keywords());
        literature.setCitationCount(request.citationCount() == null ? 0 : Math.max(request.citationCount(), 0));
        if (request.fileId() != null) {
            FileMetadata file = requireBindableFile(request.fileId(), operator, scenario);
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

    /**
     * 可读性校验：管理员、上传者、以及公开且已发布的文献对登录用户可读。
     * 上传即发布后，正常数据都满足第三类，这里保留判断以兼容历史数据。
     */
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

    private CurrentUser requireAdmin(String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("literature role denied, scenario={}, userId={}, roleCode={}", scenario, currentUser.getId(), currentUser.getRoleCode());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return currentUser;
    }

    /**
     * 批量映射文献响应并补齐学科名称。
     * <p>检索（语义 / 混合 / 自然语言）命中文献同样需要学科名，供结果页展示，因此抽出复用。</p>
     */
    List<LiteratureResponse> toResponses(List<Literature> literatures) {
        if (literatures == null || literatures.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = categoryNames(literatures);
        return literatures.stream().map(item -> toResponse(item, names.get(item.getCategoryId()))).toList();
    }

    private Map<Long, String> categoryNames(Page<Literature> result) {
        return categoryNames(result.getRecords());
    }

    private Map<Long, String> categoryNames(List<Literature> literatures) {
        var categoryIds = literatures.stream()
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
                literature.getLiteratureType(), literature.getCategoryId(), categoryName,
                literature.getPublishDate(), literature.getOnlinePublishDate(), literature.getLanguage(),
                literature.getUploaderId(), literature.getFileId(), literature.getFileUrl(), literature.getFileType(),
                literature.getFileSize(), literature.getAbstractText(), literature.getAiSummary(),
                literature.getReferencesText(), literature.getKeywords(),
                literature.getReadCount(), literature.getDownloadCount(),
                literature.getCitationCount() == null ? 0 : literature.getCitationCount(),
                literature.getCreatedAt(), literature.getUpdatedAt());
    }
}
