package com.xinfei.module.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.export.model.ExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步导出任务 Mapper（O-25）
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTask> {
}
