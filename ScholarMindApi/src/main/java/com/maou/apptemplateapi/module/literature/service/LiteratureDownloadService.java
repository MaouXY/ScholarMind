package com.maou.apptemplateapi.module.literature.service;

import com.maou.apptemplateapi.common.config.StorageProperties;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import com.maou.apptemplateapi.module.literature.dto.LiteratureDownloadResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteraturePreviewResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Set;

/**
 * 文献下载与原文预览：先做可读权限校验，再生成 RustFS 预签名地址。
 * 使用预签名而非服务端转发，避免大文件占用应用内存与带宽。
 * <p>下载（download）会累加下载次数；预览（preview）供阅读页 iframe 内联渲染，不计数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureDownloadService {

    /** 浏览器可内联渲染的内容类型：PDF 走内置阅读器，纯文本走文本视图 */
    private static final Set<String> INLINE_VIEWABLE_TYPES = Set.of("application/pdf", "text/plain");

    private final LiteratureService literatureService;
    private final LiteratureMapper literatureMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final S3Presigner rustfsS3Presigner;
    private final StorageProperties storageProperties;

    @Transactional
    public LiteratureDownloadResponse download(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-download");
        FileMetadata file = requireBoundFile(literature, currentUser, "literature-download");
        String url = presign(file, currentUser, literatureId, "literature-download");
        long expireMinutes = previewExpireMinutes();

        literature.setDownloadCount((literature.getDownloadCount() == null ? 0 : literature.getDownloadCount()) + 1);
        literatureMapper.updateById(literature);
        log.info("literature download presigned, scenario=literature-download, literatureId={}, userId={}, fileId={}, fileName={}, sizeBytes={}, downloadCount={}, expireMinutes={}",
                literatureId, currentUser.getId(), file.getId(), file.getOriginalName(), file.getSizeBytes(),
                literature.getDownloadCount(), expireMinutes);
        return new LiteratureDownloadResponse(literature.getId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), url, expireMinutes * 60, literature.getDownloadCount());
    }

    /**
     * 原文预览票据：与下载同权限、同预签名，但不累加下载次数，
     * 因此阅读页刷新或切换分块不会污染下载统计。
     */
    public LiteraturePreviewResponse preview(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-preview");
        FileMetadata file = requireBoundFile(literature, currentUser, "literature-preview");
        String url = presignInline(file, currentUser, literatureId);
        long expireMinutes = previewExpireMinutes();
        boolean inlineViewable = file.getContentType() != null && INLINE_VIEWABLE_TYPES.contains(file.getContentType());
        log.info("literature preview presigned, scenario=literature-preview, literatureId={}, userId={}, fileId={}, contentType={}, inlineViewable={}, expireMinutes={}",
                literatureId, currentUser.getId(), file.getId(), file.getContentType(), inlineViewable, expireMinutes);
        return new LiteraturePreviewResponse(literature.getId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), url, expireMinutes * 60, inlineViewable);
    }

    private FileMetadata requireBoundFile(Literature literature, CurrentUser currentUser, String scenario) {
        if (literature.getFileId() == null) {
            log.warn("literature file rejected, scenario={}, reason=no-file-bound, literatureId={}, userId={}",
                    scenario, literature.getId(), currentUser.getId());
            throw new BusinessException(ErrorCode.LITERATURE_FILE_MISSING);
        }
        FileMetadata file = fileMetadataMapper.selectById(literature.getFileId());
        if (file == null || file.getDeleted() != 0) {
            log.warn("literature file rejected, scenario={}, reason=file-missing, literatureId={}, fileId={}, userId={}",
                    scenario, literature.getId(), literature.getFileId(), currentUser.getId());
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    private String presign(FileMetadata file, CurrentUser currentUser, Long literatureId, String scenario) {
        long expireMinutes = previewExpireMinutes();
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expireMinutes))
                    .getObjectRequest(builder -> builder
                            .bucket(file.getBucketName())
                            .key(file.getObjectKey()))
                    .build();
            return rustfsS3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            log.error("literature presign failed, scenario={}, literatureId={}, fileId={}, bucket={}, objectKey={}, userId={}",
                    scenario, literatureId, file.getId(), file.getBucketName(), file.getObjectKey(), currentUser.getId(), exception);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 内联预览专用预签名：显式覆盖响应头，让浏览器按 UTF-8 解码纯文本、按 inline 处理 PDF。
     * <p>对象存储里的 text/plain 不带 charset，iframe 默认会用本地编码解码，中文会乱码；
     * 同时显式声明 Content-Disposition=inline，避免个别客户端把预览当成下载。</p>
     */
    private String presignInline(FileMetadata file, CurrentUser currentUser, Long literatureId) {
        long expireMinutes = previewExpireMinutes();
        String contentType = inlineContentType(file.getContentType());
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expireMinutes))
                    .getObjectRequest(builder -> builder
                            .bucket(file.getBucketName())
                            .key(file.getObjectKey())
                            .responseContentType(contentType)
                            .responseContentDisposition("inline"))
                    .build();
            return rustfsS3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            log.error("literature inline presign failed, scenario=literature-preview, literatureId={}, fileId={}, bucket={}, objectKey={}, userId={}",
                    literatureId, file.getId(), file.getBucketName(), file.getObjectKey(), currentUser.getId(), exception);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /** 纯文本补 charset=utf-8；PDF 等二进制类型原样返回 */
    private String inlineContentType(String storedType) {
        if (storedType == null || storedType.isBlank()) {
            return "application/octet-stream";
        }
        if (storedType.startsWith("text/")) {
            return storedType.contains("charset") ? storedType : storedType + "; charset=utf-8";
        }
        return storedType;
    }

    private long previewExpireMinutes() {
        return Math.max(storageProperties.getPreviewExpireMinutes(), 1);
    }
}
