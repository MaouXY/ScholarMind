package com.maou.apptemplateapi.module.file.service;

import com.maou.apptemplateapi.common.config.FileProperties;
import com.maou.apptemplateapi.common.config.StorageProperties;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.file.dto.FileResponse;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 文件存储服务：把文件写入 RustFS（S3 兼容对象存储）并登记 file_metadata。
 *
 * 说明：上传后 bizType 为 TEMP（临时文件），业务归属由各业务模块自行维护——
 * 文献附件通过 literature.file_id 建立绑定，文献下载经 LiteratureDownloadService 生成预签名地址。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final String BIZ_TEMP = "TEMP";

    private final FileMetadataMapper fileMetadataMapper;
    private final FileProperties fileProperties;
    private final StorageProperties storageProperties;
    private final S3Client rustfsS3Client;

    @Transactional
    public FileResponse upload(MultipartFile file) {
        CurrentUser currentUser = CurrentUserProvider.require();
        validateFile(file, currentUser);
        String objectKey = buildObjectKey(file.getOriginalFilename(), currentUser);
        String bucket = storageProperties.getRustfs().getBucket();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            rustfsS3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            log.error("file upload failed, scenario=file-upload, uploaderId={}, roleCode={}, originalName={}, sizeBytes={}",
                    currentUser.getId(), currentUser.getRoleCode(), file.getOriginalFilename(), file.getSize(), exception);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (RuntimeException exception) {
            log.error("file upload failed, scenario=file-upload-rustfs, uploaderId={}, roleCode={}, bucket={}, objectKey={}, originalName={}, sizeBytes={}",
                    currentUser.getId(), currentUser.getRoleCode(), bucket, objectKey, file.getOriginalFilename(), file.getSize(), exception);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        FileMetadata metadata = new FileMetadata();
        metadata.setOriginalName(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unknown");
        metadata.setObjectKey(objectKey);
        metadata.setBucketName(bucket);
        metadata.setContentType(file.getContentType());
        metadata.setSizeBytes(file.getSize());
        metadata.setUploaderId(currentUser.getId());
        metadata.setUploaderRole(currentUser.getRoleCode());
        metadata.setBizType(BIZ_TEMP);
        metadata.setPublicUrl(buildPublicUrl(bucket, objectKey));
        fileMetadataMapper.insert(metadata);
        log.info("file uploaded, scenario=file-upload, fileId={}, uploaderId={}, roleCode={}, bucket={}, objectKey={}, contentType={}, sizeBytes={}",
                metadata.getId(), currentUser.getId(), currentUser.getRoleCode(), bucket, objectKey,
                file.getContentType(), file.getSize());
        return toResponse(metadata);
    }

    private void validateFile(MultipartFile file, CurrentUser currentUser) {
        if (file == null || file.isEmpty()) {
            log.warn("file upload rejected, scenario=file-upload, reason=empty-file, uploaderId={}", currentUser.getId());
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
        if (file.getSize() > fileProperties.getMaxSize()) {
            log.warn("file upload rejected, scenario=file-upload, reason=size-exceeded, uploaderId={}, sizeBytes={}, maxSize={}",
                    currentUser.getId(), file.getSize(), fileProperties.getMaxSize());
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (!fileProperties.getAllowedContentTypes().isEmpty()
                && !fileProperties.getAllowedContentTypes().contains(file.getContentType())) {
            log.warn("file upload rejected, scenario=file-upload, reason=content-type-invalid, uploaderId={}, contentType={}",
                    currentUser.getId(), file.getContentType());
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
    }

    private String buildObjectKey(String originalName, CurrentUser currentUser) {
        String suffix = "";
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        return "%s/%s/%s/%d/%s%s".formatted(
                storageProperties.getBasePath(),
                currentUser.getRoleCode().toLowerCase(),
                LocalDate.now(),
                currentUser.getId(),
                UUID.randomUUID(),
                suffix);
    }

    private String buildPublicUrl(String bucket, String objectKey) {
        String endpoint = storageProperties.getRustfs().getEndpoint();
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return "%s/%s/%s".formatted(normalizedEndpoint, bucket, objectKey);
    }

    private FileResponse toResponse(FileMetadata metadata) {
        return new FileResponse(metadata.getId(), metadata.getOriginalName(), metadata.getObjectKey(), metadata.getBucketName(),
                metadata.getContentType(), metadata.getSizeBytes(), metadata.getUploaderId(), metadata.getUploaderRole(),
                metadata.getBizType(), metadata.getBizId(), metadata.getPublicUrl(), metadata.getCreatedAt());
    }
}
