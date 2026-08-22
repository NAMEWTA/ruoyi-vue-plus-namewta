package org.dromara.system.oss.upload;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.exception.OssErrorCode;
import org.dromara.common.oss.exception.S3StorageException;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * common-oss 的上传控制面适配器。
 */
@Component
public class DefaultOssUploadObjectStore implements OssUploadObjectStore {

    @Override
    public PreparedUpload prepare(String objectPrefix, String fileName, String contentType,
                                  String fingerprintDigest, OssUploadMode mode, Duration presignTtl) {
        OssClient client = OssFactory.instance();
        if (mode == OssUploadMode.MULTIPART && !client.capabilities().multipartUpload()) {
            throw new OssUploadException(OssUploadError.INVALID_POLICY, "当前 OSS Provider 不支持 Multipart");
        }
        String key = client.buildPathKey(objectPrefix, fileName);
        String bucket = client.config().bucket().orElseThrow(() ->
            new OssUploadException(OssUploadError.INVALID_POLICY, "OSS Provider 未配置 Bucket"));
        OssObjectOptions options = new OssObjectOptions(contentType,
            Map.of("upload-fingerprint", fingerprintDigest), null);
        if (mode == OssUploadMode.SINGLE) {
            return new PreparedUpload(client.clientId(), bucket, key, null,
                client.presignPut(key, presignTtl, options));
        }
        OssMultipartUpload upload = client.createMultipartUpload(key, options);
        return new PreparedUpload(client.clientId(), bucket, key, upload.uploadId(), null);
    }

    @Override
    public List<OssUploadContracts.SignedPart> signParts(OssUploadTicket ticket, List<Integer> partNumbers,
                                                         Duration ttl) {
        OssClient client = client(ticket.service());
        return partNumbers.stream().map(number -> new OssUploadContracts.SignedPart(number,
            client.presignUploadPart(ticket.objectKey(), ticket.uploadId(), number, ttl))).toList();
    }

    @Override
    public List<OssMultipartPart> listParts(OssUploadTicket ticket) {
        return client(ticket.service()).listParts(ticket.objectKey(), ticket.uploadId());
    }

    @Override
    public void completeMultipart(OssUploadTicket ticket, List<OssCompletedPart> parts) {
        client(ticket.service()).completeMultipartUpload(ticket.objectKey(), ticket.uploadId(), parts);
    }

    @Override
    public Optional<OssObjectStat> headIfPresent(OssUploadTicket ticket) {
        try {
            return Optional.of(client(ticket.service()).headObject(ticket.objectKey()));
        } catch (S3StorageException e) {
            if (e.code() == OssErrorCode.OBJECT_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public byte[] readPrefix(OssUploadTicket ticket, int length) {
        return client(ticket.service()).download(ticket.objectKey(), (result, input) -> {
            try {
                return input.readNBytes(length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void abort(OssUploadCleanupRecord cleanup) {
        if (cleanup.mode() == OssUploadMode.MULTIPART && cleanup.uploadId() != null) {
            client(cleanup.service()).abortMultipartUpload(cleanup.objectKey(), cleanup.uploadId());
        }
    }

    @Override
    public void deleteObject(OssUploadCleanupRecord cleanup) {
        client(cleanup.service()).delete(cleanup.objectKey());
    }

    private OssClient client(String service) {
        return OssFactory.instance(service);
    }
}
