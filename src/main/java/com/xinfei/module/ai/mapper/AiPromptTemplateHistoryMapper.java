package com.xinfei.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.ai.model.AiPromptTemplateHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 提示词模板历史版本 Mapper
 *
 * @author xinfei
 * @since 2026-09-30
 */
@Mapper
public interface AiPromptTemplateHistoryMapper extends BaseMapper<AiPromptTemplateHistory> {
}
