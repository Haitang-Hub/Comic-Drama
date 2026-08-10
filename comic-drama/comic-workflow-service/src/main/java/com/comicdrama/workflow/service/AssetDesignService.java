package com.comicdrama.workflow.service;

import com.comicdrama.workflow.entity.AssetDesign;
import com.comicdrama.workflow.mapper.AssetDesignMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AssetDesignService extends AbstractWorkflowService<AssetDesignMapper, AssetDesign> {

    /** 按 ID 更新资产设计字段 */
    public void updateAssetDesign(Long taskId, Long assetId, Map<String, Object> fields) {
        AssetDesign entity = this.getById(assetId);
        if (entity == null) {
            throw new RuntimeException("资产设计不存在 id=" + assetId);
        }
        if (fields != null) {
            if (fields.containsKey("assetName")) entity.setAssetName((String) fields.get("assetName"));
            if (fields.containsKey("assetDesc")) entity.setAssetDesc((String) fields.get("assetDesc"));
            if (fields.containsKey("derivedFrom")) entity.setDerivedFrom((String) fields.get("derivedFrom"));
            if (fields.containsKey("resourceUrl")) entity.setResourceUrl((String) fields.get("resourceUrl"));
        }
        this.updateById(entity);
    }
}
