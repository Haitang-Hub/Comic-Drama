package com.comicdrama.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充：
 * insertFill -> createTime/updateTime/deleted=0
 * updateFill -> updateTime
 */
@Component
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";
    private static final String DELETED = "deleted";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        // 逻辑删除默认 0
        if (metaObject.hasSetter(DELETED)) {
            Object val = getFieldValByName(DELETED, metaObject);
            if (val == null) {
                this.strictInsertFill(metaObject, DELETED, Integer.class, 0);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
