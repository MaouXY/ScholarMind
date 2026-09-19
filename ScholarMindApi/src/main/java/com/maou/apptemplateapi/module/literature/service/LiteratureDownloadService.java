package com.maou.apptemplateapi.module.literature.service;

import com.maou.apptemplateapi.common.config.StorageProperties;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import com.maou.apptemplateapi.module.literature.dto.LiteratureDownloadResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * 文献下载：先做可读权限校验，再生成 RustFS 预签名下载地址，并累加下载次数。
 * 使用预签名而非服务端转发，避免大文件占用应用内存与带宽。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureDownloadService {

    private final LiteratureService literatureService;
    private final LiteratureMapper literatureMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final S3Presigner rustfsS3Presigner;
    private final StorageProperties storageProperties;

    @Transactional
    public LiteratureDownloadResponse download(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-download");
        if (literature.getFileId() == null) {
            log.warn("literature download rejected, scenario=literature-download, reason=no-file-bound, literatureId={}, userId={}",
                    literatureId, currentUser.getId());
            throw new BusinessException(ErrorCode.LITERATURE_FILE_MISSING);
        }
        FileMetadata file = fileMetadataMapper.selectById(literature.getFileId());
        if (file == null || file.getDeleted() != 0) {
            log.warn("literature download rejected, scenario=literature-download, reason=file-missing, literatureId={}, fileId={}, userId={}",
                    literatureId, literature.getFileId(), currentUser.getId());
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        long expireMinutes = Math.max(storageProperties.getPreviewExpireMinutes(), 1);
        String url;
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expireMinutes))
                    .getObjectRequest(builder -> builder
                            .bucket(file.getBucketName())
                            .key(file.getObjectKey()))
                    .build();
            url = rustfsS3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            log.error("literature download presign failed, scenario=literature-download, literatureId={}, fileId={}, bucket={}, objectKey={}, userId={}",
                    literatureId, file.getId(), file.getBucketName(), file.getObjectKey(), currentUser.getId(), exception);
            throw new BusinessException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }

        literature.setDownloadCount((literature.getDownloadCount() == null ? 0 : literature.getDownloadCount()) + 1);
        literatureMapper.updateById(literature);
        log.info("literature download presigned, scenario=literature-download, literatureId={}, userId={}, fileId={}, fileName={}, sizeBytes={}, downloadCount={}, expireMinutes={}",
                literatureId, currentUser.getId(), file.getId(), file.getOriginalName(), file.getSizeBytes(),
                literature.getDownloadCount(), expireMinutes);
        return new LiteratureDownloadResponse(literature.getId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), url, expireMinutes * 60, literature.getDownloadCount());
    }
}
