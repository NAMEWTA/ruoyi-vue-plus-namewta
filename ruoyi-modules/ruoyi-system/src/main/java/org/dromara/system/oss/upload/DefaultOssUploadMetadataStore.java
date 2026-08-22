package org.dromara.system.oss.upload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.system.domain.SysOss;
import org.dromara.system.domain.SysOssExt;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.oss.config.OssLifecycleProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

/**
 * 以 objectKey + service 保证 Complete 重试只登记一个 ossId。
 */
@Service
@RequiredArgsConstructor
public class DefaultOssUploadMetadataStore implements OssUploadMetadataStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SysOssMapper mapper;
    private final OssLifecycleProperties lifecycleProperties;

    @Override
    public Long findByObject(String service, String objectKey) {
        SysOss existing = mapper.selectOne(new LambdaQueryWrapper<SysOss>()
            .eq(SysOss::getService, service)
            .eq(SysOss::getFileName, objectKey)
            .last("limit 1"));
        return existing == null ? null : existing.getOssId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerTemporary(OssUploadTicket ticket) {
        Long existing = findByObject(ticket.service(), ticket.objectKey());
        if (existing != null) {
            return existing;
        }
        SysOssExt ext = new SysOssExt();
        ext.setFileSize(ticket.fileSize());
        ext.setContentType(ticket.contentType());
        ext.setSource("directUpload");
        ext.setIsTemp(true);
        SysOss oss = new SysOss();
        oss.setFileName(ticket.objectKey());
        oss.setOriginalName(ticket.originalName());
        oss.setFileSuffix(ticket.fileSuffix());
        oss.setUrl("");
        oss.setExt1(JSON.writeValueAsString(ext));
        oss.setService(ticket.service());
        oss.setIsTemp("Y");
        oss.setExpireTime(LocalDateTime.now().plus(lifecycleProperties.getTempRetention()));
        mapper.insert(oss);
        return oss.getOssId();
    }
}
