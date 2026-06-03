package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.LiteratureCategoryRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureCategoryResponse;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureCategoryService {

    private final LiteratureCategoryMapper categoryMapper;

    public List<LiteratureCategoryResponse> list(boolean onlyEnabled) {
        return categoryMapper.selectList(new LambdaQueryWrapper<LiteratureCategory>()
                        .eq(LiteratureCategory::getDeleted, 0)
                        .eq(onlyEnabled, LiteratureCategory::getStatus, 1)
                        .orderByAsc(LiteratureCategory::getSort)
                        .orderByAsc(LiteratureCategory::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LiteratureCategoryResponse create(LiteratureCategoryRequest request) {
        requireAdmin("admin-create-literature-category");
        validateParent(request.parentId(), "admin-create-literature-category");
        ensureNameAvailable(request.name(), request.parentId(), null, "admin-create-literature-category");
        LiteratureCategory category = new LiteratureCategory();
        apply(category, request);
        categoryMapper.insert(category);
        log.info("literature category created, scenario=admin-create-literature-category, categoryId={}, name={}, parentId={}",
                category.getId(), category.getName(), category.getParentId());
        return toResponse(categoryMapper.selectById(category.getId()));
    }

    @Transactional
    public LiteratureCategoryResponse update(Long id, LiteratureCategoryRequest request) {
        requireAdmin("admin-update-literature-category");
        LiteratureCategory category = requireCategory(id, "admin-update-literature-category");
        if (id.equals(request.parentId())) {
            log.warn("literature category update rejected, scenario=admin-update-literature-category, reason=self-parent, categoryId={}", id);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "category parent cannot be itself");
        }
        validateParent(request.parentId(), "admin-update-literature-category");
        ensureNameAvailable(request.name(), request.parentId(), id, "admin-update-literature-category");
        apply(category, request);
        categoryMapper.updateById(category);
        log.info("literature category updated, scenario=admin-update-literature-category, categoryId={}, name={}, parentId={}",
                category.getId(), category.getName(), category.getParentId());
        return toResponse(categoryMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin("admin-delete-literature-category");
        requireCategory(id, "admin-delete-literature-category");
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<LiteratureCategory>()
                .eq(LiteratureCategory::getParentId, id)
                .eq(LiteratureCategory::getDeleted, 0));
        if (childCount > 0) {
            log.warn("literature category delete rejected, scenario=admin-delete-literature-category, reason=has-children, categoryId={}, childCount={}",
                    id, childCount);
            throw new BusinessException(ErrorCode.CONFLICT, "category has children");
        }
        categoryMapper.deleteById(id);
        log.info("literature category deleted, scenario=admin-delete-literature-category, categoryId={}", id);
    }

    LiteratureCategory requireCategory(Long id, String scenario) {
        if (id == null) {
            return null;
        }
        LiteratureCategory category = categoryMapper.selectById(id);
        if (category == null || category.getDeleted() != 0) {
            log.warn("literature category not found, scenario={}, categoryId={}", scenario, id);
            throw new BusinessException(ErrorCode.LITERATURE_CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private void validateParent(Long parentId, String scenario) {
        if (parentId != null) {
            requireCategory(parentId, scenario);
        }
    }

    private void ensureNameAvailable(String name, Long parentId, Long excludeId, String scenario) {
        long count = categoryMapper.selectCount(new LambdaQueryWrapper<LiteratureCategory>()
                .eq(LiteratureCategory::getName, name)
                .eq(parentId != null, LiteratureCategory::getParentId, parentId)
                .isNull(parentId == null, LiteratureCategory::getParentId)
                .eq(LiteratureCategory::getDeleted, 0)
                .ne(excludeId != null, LiteratureCategory::getId, excludeId));
        if (count > 0) {
            log.warn("literature category duplicated, scenario={}, name={}, parentId={}", scenario, name, parentId);
            throw new BusinessException(ErrorCode.LITERATURE_CATEGORY_DUPLICATED);
        }
    }

    private CurrentUser requireAdmin(String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("literature category role denied, scenario={}, userId={}, roleCode={}", scenario, currentUser.getId(), currentUser.getRoleCode());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return currentUser;
    }

    private void apply(LiteratureCategory category, LiteratureCategoryRequest request) {
        category.setName(request.name());
        category.setParentId(request.parentId());
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(request.status());
    }

    private LiteratureCategoryResponse toResponse(LiteratureCategory category) {
        return new LiteratureCategoryResponse(category.getId(), category.getName(), category.getParentId(),
                category.getSort(), category.getStatus(), category.getCreatedAt(), category.getUpdatedAt());
    }
}
