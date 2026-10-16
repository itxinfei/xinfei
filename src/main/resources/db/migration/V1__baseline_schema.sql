-- ============================================================================
-- V1 基线：xinfei 全量表结构（129 张表），截至 2026-10-16
-- ----------------------------------------------------------------------------
-- 来源：开发库 xinfei 实测 `mysqldump --no-data --default-character-set=utf8mb4`，
--       仅归一化 AUTO_INCREMENT 计数器，其余 DDL（列/索引/字符集/注释）逐字保留，
--       与运行时真实结构一致（不做"顺手美化"，保证基线可复核）。
--
-- 执行语义（配合 spring.flyway.baseline-on-migrate=true + baseline-version=2）：
--   - 已有业务表的库（本地开发库、既有部署）：不执行 V1/V2，只写入一条 version=2 的
--     BASELINE 记账行，后续从 V3 开始增量迁移；
--   - 空库（新环境首次启动）：执行 V1 建全 schema，再由 V2 写入最小可登录数据。
--   ⇒ 迁移编号请从 V3 起递增；V1/V2 属"前 Flyway 时代"的状态快照，禁止再改动
--     （改动会改变 checksum，导致已迁移库 validate 失败）。
--
-- 已并入的历史手工脚本效果（db/ 下那 5 份不再作为迁移执行，仅历史留档）：
--   20261002_groupbuy_unique_participation.sql / 20261002_settlement_unique_index.sql
--   20261006_department.sql / 20261006_group_meal.sql / 20261006_meal_subsidy.sql
--
-- 明确不含：
--   - 演示业务数据（db/xinfei.sql 全量导出，本地保留、不入版本库）
--   - 3 个手工迁移辅助存储过程 add_col_if_not_exists / create_index_if_not_exists /
--     safe_create_index（只服务旧手工流程，Flyway 接管后无用；未纳入也不影响运行）
--
-- 基线如实携带的既有缺陷（不在基线里偷偷修，修复另开 V3+ 迁移，详见 docs/FLYWAY_MIGRATION.md）：
--   - 175 处列注释是 GBK 乱码（如 '绉熸埛ID'），涉及 76 张表，源自历史错误字符集导入
--   - src/test/resources/schema*.sql 测试夹具与本基线不一致：缺 invoice_record /
--     invoice_title 两张表、缺约 200 个二级索引（含 uk_groupbuy_tenant_user、
--     uk_daily_settlement_tenant_date 两个业务唯一约束）
-- ============================================================================


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) NOT NULL COMMENT '收货人',
  `sex` tinyint DEFAULT NULL COMMENT '性别 0 男 1 女',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `province_code` varchar(12) DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) DEFAULT NULL COMMENT '区级名称',
  `street_name` varchar(50) DEFAULT NULL COMMENT '街道/乡镇',
  `community` varchar(100) DEFAULT NULL COMMENT '小区/大厦',
  `building` varchar(25) DEFAULT NULL COMMENT '楼栋',
  `unit` varchar(25) DEFAULT NULL COMMENT '单元',
  `floor` varchar(25) DEFAULT NULL COMMENT '楼层',
  `room_no` varchar(25) DEFAULT NULL COMMENT '门牌号',
  `detail` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0 否 1是',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度（GCJ-02）',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度（GCJ-02）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_user` bigint NOT NULL COMMENT '修改人',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`),
  KEY `idx_address_user` (`user_id`),
  KEY `idx_address_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地址簿';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_attachment` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `actor_type` varchar(16) NOT NULL COMMENT '上传者身份：EMPLOYEE/CUSTOMER',
  `owner_id` bigint NOT NULL COMMENT '上传者用户ID',
  `scene` varchar(50) DEFAULT NULL COMMENT '上传场景',
  `file_name` varchar(200) NOT NULL COMMENT '存储文件名（UUID）',
  `original_name` varchar(200) DEFAULT NULL COMMENT '原始文件名',
  `content_type` varchar(100) NOT NULL COMMENT 'MIME类型：image/jpeg、image/png、image/webp',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `width` int DEFAULT NULL COMMENT '图片宽度（像素）',
  `height` int DEFAULT NULL COMMENT '图片高度（像素）',
  `sha256` varchar(64) NOT NULL COMMENT '文件内容SHA256',
  `storage_path` varchar(500) NOT NULL COMMENT '相对存储路径：images/ai/{tenantId}/{actorType}/{uuid}.{ext}',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_owner` (`tenant_id`,`actor_type`,`owner_id`),
  KEY `idx_owner_scene` (`owner_id`,`scene`),
  KEY `idx_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI聊天图片附件';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL COMMENT '主键',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话ID（UUID）',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `actor_type` varchar(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '归属身份类型：EMPLOYEE/CUSTOMER',
  `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
  `scene` varchar(50) DEFAULT NULL COMMENT '场景',
  `message_count` int DEFAULT '0' COMMENT '消息数量',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_user` bigint DEFAULT NULL COMMENT 'is_deleted',
  `update_user` bigint DEFAULT NULL COMMENT 'create_user',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_tenant_actor` (`tenant_id`,`actor_type`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话会话';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_chunk` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `doc_id` bigint NOT NULL COMMENT '文档ID',
  `chunk_index` int NOT NULL COMMENT '序号',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '内容',
  `embedding` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '向量JSON',
  `embed_model` varchar(100) DEFAULT NULL COMMENT '鍚戦噺妯″瀷鍚',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_doc` (`tenant_id`,`doc_id`),
  FULLTEXT KEY `ft_content` (`content`) /*!50100 WITH PARSER `ngram` */
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI鐭ヨ瘑搴撳垏鍧';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_doc` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档标题',
  `doc_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'TEXT' COMMENT '类型：FAQ/TEXT',
  `audience` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'BOTH' COMMENT '受众：CUSTOMER/MERCHANT/BOTH',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '内容',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/INDEXING/READY/FAILED）',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '切块数量',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '失败/降级原因',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除（0-未删除 1-已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_tenant_audience` (`tenant_id`,`audience`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI鐭ヨ瘑搴撴枃妗';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message` (
  `id` bigint NOT NULL COMMENT '主键',
  `conversation_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `role` varchar(20) NOT NULL COMMENT '角色：user/assistant',
  `content` text COMMENT '消息内容',
  `attachments` varchar(1000) DEFAULT NULL COMMENT '附件列表JSON',
  `message_type` varchar(20) DEFAULT 'text' COMMENT '消息类型',
  `status` varchar(20) NOT NULL DEFAULT 'completed' COMMENT '消息状态：completed/stopped/failed',
  `client_msg_id` varchar(64) DEFAULT NULL COMMENT '前端消息幂等键',
  `feedback` varchar(10) DEFAULT NULL COMMENT '反馈类型',
  `dish_ids` varchar(500) DEFAULT NULL COMMENT '推荐菜品ID（JSON）',
  `tokens_used` int DEFAULT '0' COMMENT 'Token使用量',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'is_deleted',
  `create_user` bigint DEFAULT NULL COMMENT 'update_time',
  `update_user` bigint DEFAULT NULL COMMENT 'create_user',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_client_msg` (`conversation_id`,`client_msg_id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI消息记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_prompt_template` (
  `id` bigint NOT NULL COMMENT '主键',
  `code` varchar(64) NOT NULL COMMENT '模板编码：{type}_{scene}，如 system_order_assistant',
  `scene` varchar(50) NOT NULL COMMENT '场景：order_assistant/dish_desc/business_analysis/marketing',
  `type` varchar(16) NOT NULL COMMENT '类型：SYSTEM/WELCOME/QUICK',
  `title` varchar(100) NOT NULL COMMENT '模板名称（后台展示）',
  `content` text COMMENT '提示词/欢迎语文本（SYSTEM/WELCOME 使用）',
  `quick_questions` varchar(1000) DEFAULT NULL COMMENT '快捷问题JSON数组（QUICK 使用）',
  `builtin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否内置：内置不可删，可重置默认',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `version` int NOT NULL DEFAULT '1' COMMENT '模板版本（内置默认内容版本）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_scene_type` (`scene`,`type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI提示词模板';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_prompt_template_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鑷??涓婚敭',
  `template_id` bigint NOT NULL COMMENT '鎵?睘妯℃澘ID锛坅i_prompt_template.id锛',
  `code` varchar(64) NOT NULL COMMENT '妯℃澘缂栫爜锛堝揩鐓ф椂鐐癸級',
  `scene` varchar(50) NOT NULL COMMENT '鍦烘櫙锛堝揩鐓ф椂鐐癸級',
  `type` varchar(16) NOT NULL COMMENT '绫诲瀷 SYSTEM/WELCOME/QUICK锛堝揩鐓ф椂鐐癸級',
  `title` varchar(100) NOT NULL COMMENT '妯℃澘鍚嶇О锛堝揩鐓ф椂鐐癸級',
  `content` text COMMENT '姝ｆ枃鍐呭?锛堝揩鐓ф椂鐐癸級',
  `quick_questions` varchar(1000) DEFAULT NULL COMMENT '蹇?嵎闂??JSON锛堝揩鐓ф椂鐐癸級',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '鏄?惁鍚?敤锛堝揩鐓ф椂鐐癸級',
  `version` int NOT NULL COMMENT '璇ュ揩鐓у?搴旂殑妯℃澘鐗堟湰鍙',
  `operator_id` bigint DEFAULT NULL COMMENT '瑙﹀彂蹇?収鐨勬搷浣滀汉锛堝憳宸?D锛',
  `create_time` datetime NOT NULL COMMENT '蹇?収鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_apt_history_template` (`template_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI鎻愮ず璇嶆ā鏉垮巻鍙茬増鏈?紙鏇存柊/閲嶇疆鍓嶈嚜鍔ㄥ揩鐓э紝鏀?寔鍥炴粴锛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_provider_config` (
  `id` bigint NOT NULL COMMENT '涓婚敭',
  `provider_code` varchar(50) NOT NULL COMMENT '渚涘簲鍟嗙紪鐮�',
  `provider_name` varchar(100) NOT NULL COMMENT '渚涘簲鍟嗗悕绉�',
  `base_url` varchar(500) DEFAULT NULL COMMENT 'API鍩虹URL',
  `model_name` varchar(100) DEFAULT NULL COMMENT '妯″瀷鍚嶇О',
  `api_key` varchar(500) DEFAULT NULL COMMENT 'API钥艽娲�',
  `timeout` int DEFAULT '30' COMMENT '璇锋眰瓒呮椂鏃堕棿锛堢锛�',
  `max_tokens` int DEFAULT '2048' COMMENT '鏈�澶oken鏁�',
  `temperature` double DEFAULT '0.7' COMMENT '娓╁害鍙傛暟',
  `api_format` varchar(50) DEFAULT NULL COMMENT 'API鏍煎紡绫诲瀷',
  `extra_headers` text COMMENT '棰濆璇锋眰澶达紙JSON锛�',
  `request_template` text COMMENT '璇锋眰浣撴槧灏勬ā鏉�',
  `response_path` varchar(200) DEFAULT NULL COMMENT '鍝嶅簲瑙ｆ瀽璺緞',
  `icon_url` varchar(500) DEFAULT NULL COMMENT '鍥炬爣URL',
  `enabled` int NOT NULL DEFAULT '1' COMMENT '鏄惁鍚敤',
  `is_active` int NOT NULL DEFAULT '0' COMMENT '鏄惁婵�娲�',
  `last_test_time` datetime DEFAULT NULL COMMENT '鏈�鍚庢祴璇曟椂闂�',
  `last_test_result` varchar(50) DEFAULT NULL COMMENT '鏈�鍚庢祴璇曠粨鏋�',
  `sort` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭鍙�',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `capabilities` text COMMENT '能力开关JSON {chat,vision,tools,embedding}',
  `embedding_dimensions` int DEFAULT NULL COMMENT '向量维度（embedding模型）',
  PRIMARY KEY (`id`),
  KEY `idx_ai_provider_config_code` (`provider_code`),
  KEY `idx_ai_provider_config_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `taste_tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '口味标签（JSON数组）',
  `category_tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '分类标签（JSON数组）',
  `disliked_tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '不喜欢标签（JSON数组）',
  `allergies` varchar(500) DEFAULT NULL COMMENT '忌口/过敏信息',
  `price_preference` varchar(20) DEFAULT NULL COMMENT '价格偏好标签',
  `avg_order_amount` int DEFAULT NULL COMMENT '客单价均值（分）',
  `usual_diners` int DEFAULT NULL COMMENT '常用就餐人数',
  `user_tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '用户标签（JSON数组）',
  `frequent_dish_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '常点菜品ID（JSON数组）',
  `preferred_dining_type` varchar(20) DEFAULT NULL COMMENT '偏好的配送方式',
  `preferred_time_slot` varchar(20) DEFAULT NULL COMMENT '偏好的送达时段',
  `delivery_fee_sensitive` tinyint(1) DEFAULT '1' COMMENT '是否在意配送费',
  `confidence` decimal(5,2) DEFAULT '0.00' COMMENT '画像置信度',
  `last_analyzed_time` datetime DEFAULT NULL COMMENT '最后更新画像的时间',
  `total_conversations` int DEFAULT '0' COMMENT '总对话次数',
  `total_feedbacks` int DEFAULT '0' COMMENT '总反馈次数',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI用户画像';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attendance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `employee_name` varchar(50) DEFAULT NULL COMMENT '员工姓名',
  `date` date NOT NULL COMMENT '考勤日期',
  `check_in_time` datetime DEFAULT NULL COMMENT '签到时间',
  `check_out_time` datetime DEFAULT NULL COMMENT '签退时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '考勤状态：0=缺勤,1=正常,2=迟到,3=早退,4=请假,5=出差',
  `work_hours` decimal(5,2) DEFAULT '0.00' COMMENT '工时（小时）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_attendance_emp_date` (`employee_id`,`date`),
  KEY `idx_attendance_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考勤记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `buy_get_free` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(100) NOT NULL COMMENT '娲诲姩鍚嶇О',
  `description` varchar(500) DEFAULT NULL COMMENT '鎻忚堪',
  `buy_quantity` int NOT NULL COMMENT '璐拱鏁伴噺',
  `get_quantity` int NOT NULL COMMENT '璧犲搧鏁伴噺',
  `dish_id` bigint DEFAULT NULL COMMENT '閫傜敤鑿滃搧ID',
  `setmeal_id` bigint DEFAULT NULL COMMENT '閫傜敤濂楅ID',
  `gift_dish_id` bigint NOT NULL COMMENT '璧犲搧鑿滃搧ID',
  `gift_dish_name` varchar(100) DEFAULT NULL COMMENT '璧犲搧鑿滃搧鍚嶇О',
  `min_order_amount` decimal(10,2) DEFAULT NULL COMMENT '鏈�浣庤鍗曢噾棰�',
  `max_times_per_order` int DEFAULT NULL COMMENT '姣忓崟鏈�澶氳Е鍙戞鏁�',
  `start_time` datetime NOT NULL COMMENT '寮�濮嬫椂闂�',
  `end_time` datetime NOT NULL COMMENT '缁撴潫鏃堕棿',
  `status` tinyint DEFAULT '0' COMMENT '鐘舵�� 0鑽夌 1鐢熸晥 2鏆傚仠 3缁撴潫',
  `usage_count` int DEFAULT '0' COMMENT '宸蹭娇鐢ㄦ鏁�',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  PRIMARY KEY (`id`),
  KEY `idx_bgf_tenant` (`tenant_id`),
  KEY `idx_bgf_dish` (`dish_id`),
  KEY `idx_bgf_gift` (`gift_dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campaign_usage_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `campaign_id` bigint NOT NULL COMMENT '娲诲姩ID',
  `rule_id` bigint DEFAULT NULL COMMENT '瑙勫垯ID',
  `rule_type` int DEFAULT NULL COMMENT '绫诲瀷 1婊″噺 2鎶樻墸 3绉掓潃 4鏂板绔嬪噺 5涔拌禒',
  `quantity` int DEFAULT NULL COMMENT '鏁伴噺锛堢鏉�璐拱浠舵暟/涔拌禒璧犲搧浠舵暟锛屾弧鍑忓彲绌猴級',
  `order_id` bigint DEFAULT NULL COMMENT '璁㈠崟ID',
  `order_number` varchar(50) DEFAULT NULL COMMENT '璁㈠崟鍙�',
  `user_id` bigint DEFAULT NULL COMMENT '鐢ㄦ埛ID',
  `order_amount` decimal(10,2) DEFAULT NULL COMMENT '鍟嗗搧閲戦',
  `discount_amount` decimal(10,2) DEFAULT NULL COMMENT '浼樻儬閲戦',
  `actual_amount` decimal(10,2) DEFAULT NULL COMMENT '婊″噺鍚庨噾棰�',
  `use_time` datetime DEFAULT NULL COMMENT '浣跨敤鏃堕棿',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_cur_campaign` (`campaign_id`),
  KEY `idx_cur_user` (`user_id`),
  KEY `idx_cur_order` (`order_id`),
  KEY `idx_cur_time` (`use_time`),
  KEY `idx_cur_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cashier_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `order_id` bigint DEFAULT NULL COMMENT '璁㈠崟ID',
  `order_number` varchar(50) DEFAULT NULL COMMENT '璁㈠崟',
  `pay_type` int DEFAULT NULL COMMENT '鏀堕摱绫诲瀷 1-鐜伴噾 2-寰� 3-鏀� 4-閾� 5-浼氬憳鍌�',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '鏀堕摱閲�',
  `actual_amount` decimal(10,2) DEFAULT NULL COMMENT '瀹炴敹閲�',
  `change_amount` decimal(10,2) DEFAULT NULL COMMENT '鎵鹃浂閲�',
  `cashier_time` datetime DEFAULT NULL COMMENT '鏀堕摱鏃堕棿',
  `cashier_id` bigint DEFAULT NULL COMMENT '鏀堕摱鍛業D',
  `cashier_name` varchar(50) DEFAULT NULL COMMENT '鏀堕摱鍛�',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `voucher_url` varchar(255) DEFAULT NULL COMMENT '付款凭证图片相对路径',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_order` (`tenant_id`,`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `type` int NOT NULL DEFAULT '1' COMMENT '绫诲瀷 1 鑿滃搧鍒嗙被 2 濂楅鍒嗙被',
  `name` varchar(64) NOT NULL COMMENT '鍒嗙被鍚嶇О',
  `sort` int NOT NULL DEFAULT '0' COMMENT '椤哄簭',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint NOT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint NOT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  PRIMARY KEY (`id`),
  KEY `idx_category_type` (`type`),
  KEY `idx_category_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complaint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `complaint_no` varchar(50) DEFAULT NULL COMMENT '鎶曡瘔缂栧彿',
  `user_id` bigint DEFAULT NULL COMMENT '鐢ㄦ埛ID',
  `user_name` varchar(50) DEFAULT NULL COMMENT '鐢ㄦ埛',
  `user_phone` varchar(20) DEFAULT NULL COMMENT '鐢ㄦ埛鎵嬫満',
  `order_id` bigint DEFAULT NULL COMMENT '璁㈠崟ID',
  `order_number` varchar(50) DEFAULT NULL COMMENT '璁㈠崟缂栧彿',
  `complaint_type` int DEFAULT NULL COMMENT '鎶曡瘔绫诲瀷 1椋熷搧璐ㄩ噺 2閰嶉�� 3鏈嶅姟鎬佸害 4浠锋牸 5鍏朵粬',
  `title` varchar(200) DEFAULT NULL COMMENT '鎶曡瘔鏍囬',
  `content` text COMMENT '鎶曡瘔鍐呭',
  `image_urls` varchar(1000) DEFAULT NULL COMMENT '鍥剧墖URL(閫楀彿鍒嗛殧)',
  `status` int DEFAULT '0' COMMENT '鐘舵�� 0寰呭鐞� 1澶勭悊涓� 2宸茶В鍐� 3宸插叧闂�',
  `handler_id` bigint DEFAULT NULL COMMENT '澶勭悊浜篒D',
  `handler_name` varchar(50) DEFAULT NULL COMMENT '澶勭悊浜�',
  `handle_result` varchar(500) DEFAULT NULL COMMENT '澶勭悊缁撴灉',
  `compensation_amount` decimal(10,2) DEFAULT NULL COMMENT '琛ュ伩閲�',
  `handle_time` datetime DEFAULT NULL COMMENT '澶勭悊鏃堕棿',
  `satisfaction` int DEFAULT NULL COMMENT '婊℃剰搴� 1-5',
  `user_feedback` varchar(500) DEFAULT NULL COMMENT '鐢ㄦ埛鍙嶉',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_cp_user` (`user_id`),
  KEY `idx_cp_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cost_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `cost_type` int DEFAULT NULL COMMENT '鎴愭湰绫诲瀷 1-椋熸潗/2-浜哄伐/3-鍏朵粬',
  `ref_id` bigint DEFAULT NULL COMMENT '鍏宠仈ID',
  `ref_name` varchar(100) DEFAULT NULL COMMENT '鍏宠仈鍚嶇О',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '鎴愭湰閲�',
  `cost_date` datetime DEFAULT NULL COMMENT '鎴愭湰鏃ユ湡',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `version` int NOT NULL DEFAULT '0' COMMENT '涔愰攣鐗堟湉',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `name` varchar(100) DEFAULT NULL COMMENT '妯℃澘鍚嶇О',
  `type` varchar(20) DEFAULT NULL COMMENT '绫诲瀷',
  `condition_amount` decimal(10,2) DEFAULT NULL COMMENT '婊″噺鏉′欢閲�',
  `discount_amount` decimal(10,2) DEFAULT NULL COMMENT '浼樻儬閲�',
  `discount_rate` decimal(3,2) DEFAULT NULL COMMENT '鎶樻墸',
  `total_count` int DEFAULT '0' COMMENT '鍙戞斁鎬绘暟',
  `remain_count` int DEFAULT '0' COMMENT '鍓╀綑鏁伴噺',
  `valid_days` int DEFAULT NULL COMMENT '鏈夋晥澶╂暟',
  `points_price` int DEFAULT NULL COMMENT '积分兑换所需积分',
  `status` int DEFAULT '1' COMMENT '鐘�',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `member_id` bigint DEFAULT NULL COMMENT '浼氬憳ID',
  `template_id` bigint DEFAULT NULL COMMENT '浼樻儬鍒告ā鏉縄D',
  `code` varchar(64) DEFAULT NULL COMMENT '浼樻儬鍒哥爜',
  `status` varchar(20) DEFAULT 'unused' COMMENT '鐘舵�� UNUSED/USED/EXPIRED',
  `used_time` datetime DEFAULT NULL COMMENT '浣跨敤鏃堕棿',
  `order_id` bigint DEFAULT NULL COMMENT '浣跨敤璁㈠崟ID',
  `expire_time` datetime DEFAULT NULL COMMENT '杩囨湡鏃堕棿',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_template` (`member_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cs_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `session_id` bigint NOT NULL COMMENT '浼氳瘽ID',
  `sender_type` int DEFAULT NULL COMMENT '鍙戦�佹柟 1鐢ㄦ埛 2瀹㈡湇 3绯荤粺',
  `sender_id` bigint DEFAULT NULL COMMENT '鍙戦�佹柟ID',
  `sender_name` varchar(50) DEFAULT NULL COMMENT '鍙戦�佹柟',
  `message_type` int DEFAULT '1' COMMENT '娑堟伅绫诲瀷 1鏂囨湰 2鍥剧墖 3璁㈠崟鍗＄墖',
  `content` text COMMENT '娑堟伅鍐呭',
  `image_url` varchar(500) DEFAULT NULL COMMENT '鍥剧墖URL',
  `is_read` int DEFAULT '0' COMMENT '鏄惁宸茶 0鍚� 1鏄�',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_csm_session` (`session_id`),
  KEY `idx_csm_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cs_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `session_no` varchar(50) DEFAULT NULL COMMENT '浼氳瘽缂栧彿',
  `user_id` bigint DEFAULT NULL COMMENT '鐢ㄦ埛ID',
  `user_name` varchar(50) DEFAULT NULL COMMENT '鐢ㄦ埛',
  `agent_id` bigint DEFAULT NULL COMMENT '瀹㈡湇ID',
  `agent_name` varchar(50) DEFAULT NULL COMMENT '瀹㈡湇濮撳悕',
  `session_type` int DEFAULT NULL COMMENT '浼氳瘽绫诲瀷 1閫氱敤 2璁㈠崟 3鎶曡瘔',
  `order_id` bigint DEFAULT NULL COMMENT '鍏宠仈璁㈠崟ID',
  `status` int DEFAULT '0' COMMENT '鐘舵�� 0绛夊緟 1杩涜涓� 2宸插叧闂�',
  `first_response_time` datetime DEFAULT NULL COMMENT '棣栨鍝嶅簲鏃堕棿',
  `close_time` datetime DEFAULT NULL COMMENT '鍏抽棴鏃堕棿',
  `satisfaction_rating` int DEFAULT NULL COMMENT '婊℃剰搴﹁瘎鍒�(1-5)',
  `user_feedback` varchar(500) DEFAULT NULL COMMENT '鐢ㄦ埛鍙嶉',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_css_user` (`user_id`),
  KEY `idx_css_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `daily_settlement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `settlement_date` date DEFAULT NULL COMMENT '缁撶畻鏃ユ湡',
  `total_revenue` decimal(10,2) DEFAULT NULL COMMENT '钀ヤ笟',
  `cash_income` decimal(10,2) DEFAULT NULL COMMENT '鐜伴噾鏀跺叆',
  `wechat_income` decimal(10,2) DEFAULT NULL COMMENT '寰挎敹鍏�',
  `alipay_income` decimal(10,2) DEFAULT NULL COMMENT '鏀诲疂鏀�',
  `bankcard_income` decimal(10,2) DEFAULT NULL COMMENT '閾跺崱鏀�',
  `other_income` decimal(10,2) DEFAULT NULL COMMENT '鍏朵粬鏀跺叆',
  `order_count` int DEFAULT NULL COMMENT '璁㈠崟鏁伴噺',
  `refund_amount` decimal(10,2) DEFAULT NULL COMMENT '娆鹃噾',
  `refund_count` int DEFAULT NULL COMMENT '娆炬暟',
  `net_income` decimal(10,2) DEFAULT NULL COMMENT '鏀跺叆',
  `material_cost` decimal(10,2) DEFAULT NULL COMMENT '椋熸潗鎴愭湰',
  `labor_cost` decimal(10,2) DEFAULT NULL COMMENT '浜哄伐鎴愭湰',
  `other_cost` decimal(10,2) DEFAULT NULL COMMENT '鍏朵粬鎴愭湰',
  `total_cost` decimal(10,2) DEFAULT NULL COMMENT '鎬绘垚',
  `gross_profit` decimal(10,2) DEFAULT NULL COMMENT '姣涘埄',
  `profit_rate` decimal(10,2) DEFAULT NULL COMMENT '姣涘埄(%)',
  `status` int DEFAULT '0' COMMENT '缁撹处鐘� 0-鏈� 1-宸茬粨',
  `settlement_time` datetime DEFAULT NULL COMMENT '缁撹处鏃堕棿',
  `settlement_user_id` bigint DEFAULT NULL COMMENT '缁撹处浜篒D',
  `settlement_user_name` varchar(50) DEFAULT NULL COMMENT '缁撹处浜�',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `version` int NOT NULL DEFAULT '0' COMMENT '涔愰攣鐗堟湉',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_settlement_tenant_date` (`tenant_id`,`settlement_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_fee_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_id` bigint NOT NULL COMMENT '规则ID',
  `start_distance` decimal(10,2) NOT NULL COMMENT '起始距离（米）',
  `end_distance` decimal(10,2) NOT NULL COMMENT '结束距离（米）',
  `fee` decimal(10,2) NOT NULL COMMENT '配送费',
  `increment_distance` decimal(10,2) DEFAULT NULL COMMENT '每增加距离（米）',
  `increment_fee` decimal(10,2) DEFAULT NULL COMMENT '增加费用',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配送费阶梯规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `platform_order_id` varchar(64) DEFAULT NULL COMMENT '骞冲彴璁㈠崟',
  `platform` varchar(20) DEFAULT NULL COMMENT '閰嶅钩',
  `order_id` bigint DEFAULT NULL COMMENT '鏈湴璁㈠崟ID锛堝叧鑱� orders.id锛屽彲绌猴級',
  `dish_summary` varchar(255) DEFAULT NULL COMMENT '鑿滃搧鎽�',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '璁㈠崟閲�',
  `user_name` varchar(50) DEFAULT NULL COMMENT '鐢ㄦ埛濮撳悕',
  `phone` varchar(20) DEFAULT NULL COMMENT '系缁�',
  `address` varchar(255) DEFAULT NULL COMMENT '閰嶅湴',
  `status` varchar(20) DEFAULT NULL COMMENT '璁㈠崟鐘�',
  `order_time` datetime DEFAULT NULL COMMENT '涓嬪崟鏃堕棿',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `created_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `version` int DEFAULT NULL COMMENT '涔愰攣鐗堟湉',
  PRIMARY KEY (`id`),
  KEY `idx_delivery_order_tenant_id` (`tenant_id`),
  KEY `idx_delivery_order_platform_order_id` (`platform_order_id`),
  KEY `idx_delivery_order_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_range_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `range_type` tinyint NOT NULL COMMENT '范围类型：1-圆形，2-多边形',
  `center_longitude` decimal(12,8) DEFAULT NULL COMMENT '中心经度',
  `center_latitude` decimal(12,8) DEFAULT NULL COMMENT '中心纬度',
  `radius` decimal(10,2) DEFAULT NULL COMMENT '半径（米）',
  `polygon_points` text COMMENT '多边形坐标点JSON',
  `fee_type` tinyint DEFAULT '1' COMMENT '配送费类型：1-固定，2-距离阶梯，3-基础+距离',
  `base_fee` decimal(10,2) DEFAULT '0.00' COMMENT '基础配送费',
  `fee_per_km` decimal(10,2) DEFAULT NULL COMMENT '每公里配送费',
  `min_fee` decimal(10,2) DEFAULT NULL COMMENT '最低配送费',
  `max_fee` decimal(10,2) DEFAULT NULL COMMENT '最高配送费',
  `free_threshold` decimal(10,2) DEFAULT NULL COMMENT '免费配送金额门槛',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配送范围规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_time_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_number` varchar(50) DEFAULT NULL,
  `rider_id` bigint DEFAULT NULL,
  `rider_name` varchar(50) DEFAULT NULL,
  `order_time` datetime DEFAULT NULL,
  `accept_time` datetime DEFAULT NULL,
  `pickup_time` datetime DEFAULT NULL,
  `deliver_time` datetime DEFAULT NULL,
  `estimated_minutes` int DEFAULT NULL,
  `actual_minutes` int DEFAULT NULL,
  `distance` decimal(10,2) DEFAULT NULL,
  `status` int DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `name` varchar(50) NOT NULL COMMENT '部门名称',
  `code` varchar(32) DEFAULT NULL COMMENT '部门编码',
  `leader_name` varchar(50) DEFAULT NULL COMMENT '负责人姓名',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_dept_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表（企业组织架构）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dining_area` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `name` varchar(50) DEFAULT NULL COMMENT '鍖哄煙鍚嶇О',
  `sort` int DEFAULT '0' COMMENT '鎺掑簭',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dining_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `queue_no` varchar(32) DEFAULT NULL COMMENT '鎺掗槦',
  `phone` varchar(20) DEFAULT NULL COMMENT '鎵嬫満',
  `user_id` bigint DEFAULT NULL COMMENT '取号顾客用户ID',
  `seat_count` int DEFAULT NULL COMMENT '浜烘暟',
  `status` varchar(20) DEFAULT NULL COMMENT '鐘舵�� WAITING/CALLED/CANCELLED/SERVED',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_queue_tenant_no` (`tenant_id`,`queue_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dining_reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `table_id` bigint DEFAULT NULL COMMENT '妗屽彴ID',
  `customer_name` varchar(50) DEFAULT NULL COMMENT '椤惧鍚�',
  `phone` varchar(20) DEFAULT NULL COMMENT '鎵嬫満',
  `user_id` bigint DEFAULT NULL COMMENT '预订顾客用户ID',
  `reserved_time` datetime DEFAULT NULL COMMENT '棰勬椂闂�',
  `seat_count` int DEFAULT NULL COMMENT '浜烘暟',
  `status` varchar(20) DEFAULT NULL COMMENT '鐘舵�� PENDING/CONFIRMED/CANCELLED/ARRIVED',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dining_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `area_id` bigint DEFAULT NULL COMMENT '鍖哄煙ID',
  `name` varchar(50) DEFAULT NULL COMMENT '妗屽彴鍚嶇О',
  `seat_count` int DEFAULT NULL COMMENT '搴т綅',
  `status` varchar(20) DEFAULT NULL COMMENT '鐘舵�� FREE/OCCUPIED/RESERVED/CLEANING',
  `min_amount` decimal(10,2) DEFAULT NULL COMMENT '浣庢秷',
  `qr_code_url` varchar(255) DEFAULT NULL COMMENT '浜岀淮鐮乁RL',
  `current_order_id` bigint DEFAULT NULL COMMENT '褰撳墠鍏宠仈璁㈠崟ID锛堝紑鍙板悗缁戝畾',
  `sort` int DEFAULT '0' COMMENT '鎺掑簭',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `discount_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `campaign_id` bigint NOT NULL COMMENT '活动ID',
  `rule_name` varchar(100) DEFAULT NULL COMMENT '规则名称',
  `scope` tinyint NOT NULL COMMENT '折扣范围：1-全场，2-指定分类，3-指定菜品，4-指定套餐',
  `discount_rate` decimal(5,4) NOT NULL COMMENT '折扣率（如8折传0.8）',
  `max_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '最大优惠金额',
  `min_consumption` decimal(10,2) DEFAULT NULL COMMENT '最低消费金额',
  `category_id` bigint DEFAULT NULL COMMENT '适用分类ID',
  `dish_id` bigint DEFAULT NULL COMMENT '适用菜品ID',
  `setmeal_id` bigint DEFAULT NULL COMMENT '适用套餐ID',
  `daily_limit` int DEFAULT NULL COMMENT '每日限用次数',
  `per_user_limit` int DEFAULT NULL COMMENT '每人限用次数',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_campaign_id` (`campaign_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='折扣规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `category_id` bigint NOT NULL COMMENT '鑿滃搧鍒嗙被id',
  `name` varchar(64) NOT NULL COMMENT '鑿滃搧鍚嶇О',
  `price` decimal(10,2) NOT NULL COMMENT '鑿滃搧浠锋牸',
  `code` varchar(64) DEFAULT NULL COMMENT '鍟嗗搧鐮�',
  `image` varchar(255) DEFAULT NULL COMMENT '鍥剧墖',
  `description` varchar(400) DEFAULT NULL COMMENT '鎻忚堪淇℃伅',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '0 鍋滃敭 1 璧峰敭',
  `sort` int DEFAULT NULL COMMENT '椤哄簭',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint NOT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint NOT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `stock_qty` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '褰撳墠搴撳瓨鏁伴噺',
  `min_stock` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鏈�浣庡簱瀛橀璀﹂槇鍊�',
  PRIMARY KEY (`id`),
  KEY `idx_dish_category` (`category_id`),
  KEY `idx_dish_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_cost` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `dish_id` bigint DEFAULT NULL COMMENT '鑿滃搧ID',
  `dish_name` varchar(100) DEFAULT NULL COMMENT '鑿滃搧鍚嶇О',
  `material_cost` decimal(10,2) DEFAULT NULL COMMENT '椋熸潗鎴愭湰',
  `labor_cost` decimal(10,2) DEFAULT NULL COMMENT '浜哄伐鎴愭湰',
  `other_cost` decimal(10,2) DEFAULT NULL COMMENT '鍏朵粬鎴愭湰',
  `total_cost` decimal(10,2) DEFAULT NULL COMMENT '鎬绘垚',
  `sale_price` decimal(10,2) DEFAULT NULL COMMENT '鍞�',
  `profit_rate` decimal(10,2) DEFAULT NULL COMMENT '姣涘埄(%)',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_evaluation` (
  `id` bigint NOT NULL COMMENT '璇勪环ID(闆姳绠楁硶)',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `order_id` bigint DEFAULT NULL COMMENT '璁㈠崟id',
  `user_id` bigint DEFAULT NULL COMMENT '璇勪环鐢ㄦ埛id',
  `user_name` varchar(64) DEFAULT NULL COMMENT '璇勪环鐢ㄦ埛',
  `dish_id` bigint DEFAULT NULL COMMENT '鑿滃搧id锛堣彍鍝佷笅鍗曟椂濉級',
  `setmeal_id` bigint DEFAULT NULL COMMENT '濂楅id锛堝椁愪笅鍗曟椂濉紝涓巇ish_id浜掓枼锛�',
  `dish_name` varchar(64) DEFAULT NULL COMMENT '鍟嗗搧鍚嶇О锛堣彍鍝佸悕/濂楅鍚嶏級',
  `star_rating` int DEFAULT NULL COMMENT '璇勫垎(1-5)',
  `content` varchar(500) DEFAULT NULL COMMENT '璇勪环鍐呭',
  `images` varchar(2000) DEFAULT NULL COMMENT '璇勪环鍥剧墖JSON鏁扮粍',
  `anonymous` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍖垮悕 0=瀹炲悕 1=鍖垮悕',
  `reply_content` varchar(500) DEFAULT NULL COMMENT '鍟嗗鍥炲鍐呭',
  `reply_time` datetime DEFAULT NULL COMMENT '鍟嗗鍥炲鏃堕棿',
  `status` int DEFAULT '0' COMMENT '瀹℃牳鐘舵�侊細0寰呭鏍革紝1閫氳繃锛�2鎷掔粷',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 0=鏈垹闄� 1=宸插垹闄�',
  PRIMARY KEY (`id`),
  KEY `idx_dish_evaluation_tenant` (`tenant_id`),
  KEY `idx_dish_evaluation_user_setmeal` (`user_id`,`setmeal_id`,`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `dish_id` bigint NOT NULL COMMENT '鑿滃搧',
  `name` varchar(64) NOT NULL COMMENT '鍙ｅ懗鍚嶇О',
  `value` varchar(255) DEFAULT NULL COMMENT '鍙ｅ懗鏁版嵁list',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint NOT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint NOT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_dish_flavor_dish` (`dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `dish_id` bigint DEFAULT NULL COMMENT '鑿滃搧ID',
  `material_id` bigint DEFAULT NULL COMMENT '椋熸潗ID',
  `usage_qty` decimal(10,3) DEFAULT NULL COMMENT '鍗曚唤鑿滃搧娑堣�楅鏉愭暟閲�',
  `sort` int DEFAULT '0' COMMENT '鎺掑簭',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_material_dish_material` (`tenant_id`,`dish_id`,`material_id`),
  KEY `idx_dish_material_tenant_id` (`tenant_id`),
  KEY `idx_dish_material_dish_id` (`dish_id`),
  KEY `idx_dish_material_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_platform_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `dish_id` bigint NOT NULL COMMENT '鏈崇粺鑿滃搧ID',
  `platform_type` varchar(32) NOT NULL COMMENT '骞冲彴绫诲瀷 MEITUAN/ELEME/DOUYIN/SELF/OTHER',
  `platform_shop_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴渚ч棬搴桰D',
  `platform_dish_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴鑿滃搧ID',
  `platform_sku_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴SKU ID',
  `price` decimal(10,2) DEFAULT NULL COMMENT '骞冲彴浠锋牸',
  `status` int NOT NULL DEFAULT '1' COMMENT '鐘� 0涓嬫灦 1涓婃灦',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_mapping_dish_platform` (`dish_id`,`platform_type`,`platform_dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_spec_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) NOT NULL COMMENT '规格组名称',
  `type` tinyint DEFAULT '1' COMMENT '类型：1-单选，2-多选',
  `required` tinyint DEFAULT '0' COMMENT '是否必选：0-否，1-是',
  `max_select` int DEFAULT NULL COMMENT '最大可选数量',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜品规格组表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_spec_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_id` bigint NOT NULL COMMENT '规格组ID',
  `name` varchar(50) NOT NULL COMMENT '选项名称',
  `price_adjust` decimal(10,2) DEFAULT '0.00' COMMENT '价格调整',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜品规格选项表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish_spec_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品ID',
  `group_id` bigint NOT NULL COMMENT '规格组ID',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dish_group` (`dish_id`,`group_id`),
  KEY `idx_dish_id` (`dish_id`),
  KEY `idx_group_id` (`group_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜品规格关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `username` varchar(50) NOT NULL COMMENT '鐢ㄦ埛鍚�',
  `name` varchar(50) DEFAULT NULL COMMENT '濮撳悕',
  `phone` varchar(11) DEFAULT NULL COMMENT '鎵嬫満鍙�',
  `sex` varchar(2) DEFAULT NULL COMMENT '鎬у埆',
  `id_number` varchar(18) DEFAULT NULL COMMENT '韬唤璇佸彿',
  `avatar` varchar(255) DEFAULT NULL COMMENT '澶村儚鍥剧墖鐩稿璺緞',
  `job_number` varchar(32) DEFAULT NULL COMMENT '宸ュ彿锛堢鎴峰唴鍞竴锛�',
  `position` varchar(32) DEFAULT NULL COMMENT '宀椾綅',
  `status` int NOT NULL DEFAULT '1' COMMENT '鐘舵�� 0:绂佺敤 1:姝ｅ父',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint NOT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint NOT NULL COMMENT '淇敼浜�',
  `password` varchar(255) NOT NULL COMMENT '瀵嗙爜',
  `password_type` varchar(32) NOT NULL DEFAULT 'MD5' COMMENT '瀵嗙爜鍔犲瘑绫诲瀷 MD5/BCRYPT',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `role` int NOT NULL DEFAULT '2' COMMENT '瑙掕壊 1:瓒呯骇绠＄悊鍛� 2:鏅�氬憳宸�',
  `department_id` bigint DEFAULT NULL COMMENT '部门ID（企业组织架构，2026-10-06）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_employee_username` (`username`),
  UNIQUE KEY `idx_employee_tenant_jobno` (`tenant_id`,`job_number`),
  KEY `idx_employee_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `employee_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `employee_id` bigint NOT NULL COMMENT '鍛樺伐ID',
  `role_id` bigint NOT NULL COMMENT '瑙掕壊ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_role` (`employee_id`,`role_id`),
  KEY `idx_employee_role_employee` (`employee_id`),
  KEY `idx_employee_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flash_sale` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(100) NOT NULL COMMENT '娲诲姩鍚嶇О',
  `description` varchar(500) DEFAULT NULL COMMENT '鎻忚堪',
  `dish_id` bigint NOT NULL COMMENT '鑿滃搧ID',
  `dish_name` varchar(100) DEFAULT NULL COMMENT '鑿滃搧鍚嶇О',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '鍘熶环',
  `flash_price` decimal(10,2) NOT NULL COMMENT '绉掓潃浠�',
  `total_quantity` int NOT NULL DEFAULT '0' COMMENT '鎬诲簱瀛�',
  `sold_quantity` int NOT NULL DEFAULT '0' COMMENT '宸插敭鏁伴噺',
  `max_per_user` int DEFAULT NULL COMMENT '姣忎汉闄愯喘',
  `start_time` datetime NOT NULL COMMENT '寮�濮嬫椂闂�',
  `end_time` datetime NOT NULL COMMENT '缁撴潫鏃堕棿',
  `status` int NOT NULL DEFAULT '0' COMMENT '鐘舵�� 0鑽夌 1杩涜涓� 2鏆傚仠 3缁撴潫',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  PRIMARY KEY (`id`),
  KEY `idx_flash_dish` (`dish_id`),
  KEY `idx_flash_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `full_reduction_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `campaign_id` bigint NOT NULL COMMENT '娲诲姩ID',
  `rule_name` varchar(100) DEFAULT NULL COMMENT '瑙勫垯鍚嶇О',
  `discount_type` int NOT NULL COMMENT '绫诲瀷 1鍑忓浐瀹� 2鎵撴姌 3璧犲搧',
  `min_amount` decimal(10,2) NOT NULL COMMENT '闂ㄦ閲戦',
  `discount_value` decimal(10,2) NOT NULL COMMENT '浼樻儬鍊�/鎶樻墸鐜�',
  `max_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '鏈�澶т紭鎯犻噾棰�',
  `gift_dish_id` bigint DEFAULT NULL COMMENT '璧犲搧鑿滃搧ID',
  `gift_quantity` int DEFAULT NULL COMMENT '璧犲搧鏁伴噺',
  `stackable` int DEFAULT '0' COMMENT '鏄惁鍙彔鍔�',
  `daily_limit` int DEFAULT NULL COMMENT '姣忔棩闄愭',
  `per_user_limit` int DEFAULT NULL COMMENT '姣忎汉闄愭',
  `sort_order` int DEFAULT '0' COMMENT '鎺掑簭',
  `status` int DEFAULT '1' COMMENT '鐘舵�� 0绂佺敤 1鍚敤',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜�',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_frr_campaign` (`campaign_id`),
  KEY `idx_frr_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_buy_campaign` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '绉熸埛ID',
  `name` varchar(100) NOT NULL COMMENT '娲诲姩鍚嶇О',
  `description` varchar(500) DEFAULT '' COMMENT '娲诲姩鎻忚堪',
  `group_id` bigint NOT NULL DEFAULT '0' COMMENT '鎷煎洟缁処D',
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT '鐘舵�侊細OPEN/CLOSED/ENDED',
  `start_time` datetime NOT NULL COMMENT '寮�濮嬫椂闂�',
  `end_time` datetime NOT NULL COMMENT '缁撴潫鏃堕棿',
  `min_members` int NOT NULL DEFAULT '2' COMMENT '鏈�灏戞垚鍥汉鏁�',
  `max_members` int NOT NULL DEFAULT '10' COMMENT '鏈�澶氭垚鍥汉鏁�',
  `original_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鍘熶环',
  `group_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鎷煎洟浠�',
  `dish_id` bigint NOT NULL DEFAULT '0' COMMENT '鑿滃搧ID',
  `dish_name` varchar(100) DEFAULT '' COMMENT '鑿滃搧鍚嶇О',
  `image` varchar(255) DEFAULT '' COMMENT '娲诲姩鍥剧墖URL',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛�0=鏈垹闄わ紝1=宸插垹闄�',
  PRIMARY KEY (`id`),
  KEY `idx_group_buy_campaign_tenant_id` (`tenant_id`),
  KEY `idx_group_buy_campaign_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_buy_participation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '绉熸埛ID',
  `group_buy_id` bigint NOT NULL COMMENT '鎷煎洟娲诲姩ID',
  `order_id` bigint NOT NULL COMMENT '璁㈠崟ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `status` varchar(20) NOT NULL DEFAULT 'JOINED' COMMENT '鐘舵�侊細JOINED/PAID/CANCELLED',
  `join_time` datetime NOT NULL COMMENT '鍙傚洟鏃堕棿',
  `pay_time` datetime DEFAULT NULL COMMENT '鏀粯鏃堕棿',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_groupbuy_tenant_user` (`group_buy_id`,`user_id`),
  KEY `idx_group_buy_participation_tenant_id` (`tenant_id`),
  KEY `idx_group_buy_participation_group_buy_id` (`group_buy_id`),
  KEY `idx_group_buy_participation_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_meal_booking` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `department_id` bigint DEFAULT NULL COMMENT '部门ID',
  `department_name` varchar(50) DEFAULT NULL COMMENT '部门名快照',
  `contact_name` varchar(50) NOT NULL COMMENT '联系人',
  `contact_phone` varchar(20) NOT NULL COMMENT '联系电话',
  `meal_date` date NOT NULL COMMENT '用餐日期',
  `meal_type` varchar(10) NOT NULL DEFAULT 'LUNCH' COMMENT '餐段 LUNCH:午餐 DINNER:晚餐',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0:待确认 1:已确认 2:已完成 3:已取消',
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '总金额（下单时快照）',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_gmb_tenant_date` (`tenant_id`,`meal_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_meal_booking_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `booking_id` bigint NOT NULL COMMENT '预订ID',
  `item_type` varchar(10) NOT NULL COMMENT '条目类型 DISH:菜品 SETMEAL:套餐',
  `item_id` bigint NOT NULL COMMENT '菜品/套餐ID',
  `item_name` varchar(100) NOT NULL COMMENT '名称快照',
  `price` decimal(10,2) NOT NULL COMMENT '单价快照',
  `quantity` int NOT NULL COMMENT '份数',
  PRIMARY KEY (`id`),
  KEY `idx_gmbi_booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint DEFAULT NULL COMMENT '申请用户ID（用户归属列，防止跨用户越权查询）',
  `order_no` varchar(32) DEFAULT NULL COMMENT '璁㈠崟鍙',
  `title_id` bigint DEFAULT NULL COMMENT '发票抬头ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '发票抬头（冗余）',
  `tax_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '税号（冗余）',
  `type` int NOT NULL DEFAULT '1' COMMENT '发票类型（1-个人 2-企业）',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '发票金额',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态（0-待申请 1-已申请 2-已开具 3-已作废）',
  `invoice_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '发票号码',
  `invoice_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '发票代码',
  `invoice_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '发票PDF地址',
  `apply_time` datetime DEFAULT NULL COMMENT '申请时间',
  `issue_time` datetime DEFAULT NULL COMMENT '开具时间',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_order` (`order_id`),
  KEY `idx_record_tenant` (`tenant_id`),
  KEY `idx_record_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票申请记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_title` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发票抬头',
  `tax_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '税号',
  `company_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '公司名称（企业发票用）',
  `type` int NOT NULL DEFAULT '1' COMMENT '发票类型（1-个人 2-企业）',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint DEFAULT NULL COMMENT '归属用户ID（用户隔离，防止跨用户越权）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_title_tenant` (`tenant_id`),
  KEY `idx_title_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票抬头';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kitchen_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '订单号',
  `order_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '用餐类型',
  `table_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '堂食桌台',
  `customer_count` int DEFAULT NULL COMMENT '用餐人数',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '工单状态（1-待制作 2-制作中 3-待取餐 4-已完成 5-已取餐）',
  `urgent` tinyint NOT NULL DEFAULT '0' COMMENT '是否加急（0-否 1-是）',
  `dish_summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '菜品摘要',
  `receive_time` datetime DEFAULT NULL COMMENT '接单/生成时间',
  `cook_start_time` datetime DEFAULT NULL COMMENT '开始制作时间',
  `ready_time` datetime DEFAULT NULL COMMENT '备餐完成时间',
  `finish_time` datetime DEFAULT NULL COMMENT '出餐完成时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `cook_duration_seconds` bigint DEFAULT NULL COMMENT '制作耗时（秒）',
  `station_code` varchar(20) DEFAULT NULL COMMENT '档口编码',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除（0-未删除 1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kitchen_order` (`order_id`),
  KEY `idx_kitchen_tenant_status` (`tenant_id`,`status`),
  KEY `idx_kitchen_finish` (`finish_time`),
  KEY `idx_kitchen_station` (`tenant_id`,`station_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后厨厨房工单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `labor_cost` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `employee_id` bigint DEFAULT NULL COMMENT '鍛樺伐ID',
  `employee_name` varchar(50) DEFAULT NULL COMMENT '鍛樺伐濮撳悕',
  `salary` decimal(10,2) DEFAULT NULL COMMENT '宸ヨ祫',
  `social_insurance` decimal(10,2) DEFAULT NULL COMMENT '绫�',
  `housing_fund` decimal(10,2) DEFAULT NULL COMMENT '鍏�',
  `other_benefits` decimal(10,2) DEFAULT NULL COMMENT '鍏朵粬绂忓埄',
  `total_cost` decimal(10,2) DEFAULT NULL COMMENT '鎬绘垚',
  `cost_month` date DEFAULT NULL COMMENT '鎴愭湰鏈堜唤',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operate_user` bigint DEFAULT NULL COMMENT '操作人ID',
  `operate_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `module` varchar(50) DEFAULT NULL COMMENT '操作模块',
  `type` varchar(50) DEFAULT NULL COMMENT '操作类型',
  `method` varchar(200) DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(200) DEFAULT NULL COMMENT '请求URL',
  `request_params` text COMMENT '请求参数',
  `response_data` text COMMENT '响应数据',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `status` int DEFAULT NULL COMMENT '状态 0:失败 1:成功',
  `error_msg` text COMMENT '错误信息',
  `cost_time` bigint DEFAULT NULL COMMENT '耗时(ms)',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_campaign` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint NOT NULL COMMENT '绉熸埛ID',
  `name` varchar(100) NOT NULL COMMENT '娲诲姩鍚嶇О',
  `description` varchar(500) DEFAULT NULL COMMENT '娲诲姩鎻忚堪',
  `campaign_type` int NOT NULL COMMENT '娲诲姩绫诲瀷 1:婊″噺 2:鎶樻墸 3:璧犲搧 4:棣栧崟 5:浼氬憳涓撲韩 6:绉掓潃',
  `target_type` int NOT NULL DEFAULT '1' COMMENT '鐩爣绫诲瀷 1鍏ㄩ儴 2鏂扮敤鎴� 3楂樹环鍊� 4娴佸け 5鎸囧畾绛夌骇',
  `target_value` varchar(500) DEFAULT NULL COMMENT '鐩爣鍊�',
  `rule_json` longtext COMMENT '瑙勫垯JSON',
  `status` int NOT NULL DEFAULT '0' COMMENT '鐘舵�� 0鑽夌 1杩涜涓� 2宸茬粨鏉� 3鏆傚仠',
  `priority` int NOT NULL DEFAULT '0' COMMENT '浼樺厛绾�',
  `start_time` datetime NOT NULL COMMENT '寮�濮嬫椂闂�',
  `end_time` datetime NOT NULL COMMENT '缁撴潫鏃堕棿',
  `max_participants` int DEFAULT NULL COMMENT '鏈�澶у弬涓庝汉鏁�',
  `current_participants` int NOT NULL DEFAULT '0' COMMENT '褰撳墠鍙備笌浜烘暟',
  `coupon_template_id` bigint DEFAULT NULL COMMENT '鍏宠仈鍒告ā鏉縄D',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_mc_tenant_status` (`tenant_id`,`status`),
  KEY `idx_mc_time` (`start_time`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `campaign_id` bigint NOT NULL COMMENT '关联营销活动ID',
  `user_id` bigint NOT NULL COMMENT '推送用户ID',
  `push_type` tinyint NOT NULL COMMENT '推送类型 1:首页弹窗 2:消息通知 3:短信 4:优惠券自动发',
  `title` varchar(200) NOT NULL COMMENT '推送标题',
  `content` varchar(1000) NOT NULL COMMENT '推送内容',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0:待推送 1:已推送 2:已领取 3:已使用',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_campaign` (`campaign_id`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='营销消息推送记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `category_id` bigint DEFAULT NULL COMMENT '鍒嗙被ID',
  `name` varchar(100) DEFAULT NULL COMMENT '鐗╂枡鍚嶇О',
  `unit` varchar(20) DEFAULT NULL COMMENT '鍗曚綅',
  `stock_qty` decimal(10,2) DEFAULT NULL COMMENT '搴撳瓨鏁伴噺',
  `min_stock` decimal(10,2) DEFAULT NULL COMMENT '鏈�灏忓簱瀛�',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '鍗曚环',
  `supplier_id` bigint DEFAULT NULL COMMENT '渚涘簲鍟咺D',
  `barcode` varchar(50) DEFAULT NULL COMMENT '鏉″舰鐮�',
  `status` int DEFAULT NULL COMMENT '鐘舵�侊紙1-姝ｅ父锛�0-绂佺敤锛�',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_material_tenant_id` (`tenant_id`),
  KEY `idx_material_category_id` (`category_id`),
  KEY `idx_material_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `name` varchar(50) DEFAULT NULL COMMENT '鍒嗙被鍚嶇О',
  `sort` int DEFAULT NULL COMMENT '鎺掑簭',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_material_category_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meal_subsidy_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '顾客用户ID（C端下单人）',
  `department_id` bigint DEFAULT NULL COMMENT '部门ID（发放时的归属部门快照）',
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '当前余额',
  `total_granted` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '累计发放',
  `total_used` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '累计核销',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态 0:冻结 1:正常',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subsidy_tenant_user` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='餐补账户表（企业内部订餐）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meal_subsidy_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '顾客用户ID',
  `account_id` bigint DEFAULT NULL COMMENT '账户ID',
  `record_type` varchar(16) NOT NULL COMMENT '流水类型 GRANT:发放 CONSUME:核销 REFUND:回充 ADJUST:调整',
  `amount` decimal(10,2) NOT NULL COMMENT '发生金额（正向）',
  `balance_after` decimal(10,2) NOT NULL COMMENT '交易后余额',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID（核销/回充）',
  `trade_no` varchar(64) DEFAULT NULL COMMENT '关联交易号（支付单tradeNo/退款单refundNo），幂等键',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人（管理员发放时）',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subsidy_record_trade` (`trade_no`,`record_type`),
  KEY `idx_subsidy_record_user` (`tenant_id`,`user_id`,`create_time`),
  KEY `idx_subsidy_record_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='餐补流水表（企业内部订餐）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `user_id` bigint DEFAULT NULL COMMENT '鍏宠仈鐢ㄦ埛ID',
  `level_id` bigint DEFAULT NULL COMMENT '浼氬憳绛夌骇ID',
  `name` varchar(50) DEFAULT NULL COMMENT '浼氬憳濮撳悕',
  `phone` varchar(11) DEFAULT NULL COMMENT '鎵嬫満',
  `points` bigint DEFAULT '0' COMMENT '绉�',
  `balance` decimal(10,2) DEFAULT '0.00' COMMENT '浣�',
  `total_consumption` decimal(10,2) DEFAULT '0.00' COMMENT '娑堣垂閲�',
  `status` int DEFAULT '1' COMMENT '鐘�',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `version` int NOT NULL DEFAULT '0' COMMENT '涔愰攣鐗堟湉',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `name` varchar(50) DEFAULT NULL COMMENT '绛夌骇鍚嶇О',
  `min_points` bigint DEFAULT '0' COMMENT '鏈�浣庣Н鍒嗚姹�',
  `max_points` bigint DEFAULT NULL COMMENT '鏈�楂樼Н鍒嗕笂闄�',
  `discount` decimal(3,2) DEFAULT '1.00' COMMENT '鎶樻墸',
  `description` varchar(200) DEFAULT NULL COMMENT '绛夌骇璇存槑',
  `sort` int DEFAULT NULL COMMENT '鎺掑簭',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `tag_name` varchar(50) NOT NULL COMMENT '标签名称',
  `tag_type` tinyint DEFAULT '1' COMMENT '标签类型（1手动添加 2自动生成）',
  `biz_tag` varchar(50) DEFAULT NULL COMMENT '业务标签',
  `tag_color` varchar(20) DEFAULT '#409EFF' COMMENT '标签颜色',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_member_biz` (`tenant_id`,`member_id`,`biz_tag`),
  KEY `idx_member_tenant` (`member_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员标签表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `name` varchar(50) NOT NULL COMMENT '菜单名称',
  `path` varchar(200) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `type` int NOT NULL DEFAULT '1' COMMENT '类型 1:菜单 2:按钮',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `new_customer_discount` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(100) NOT NULL COMMENT '娲诲姩鍚嶇О',
  `discount_type` tinyint NOT NULL COMMENT '浼樻儬绫诲瀷 1鍥哄畾閲戦 2鐧惧垎姣�',
  `discount_value` decimal(10,2) NOT NULL COMMENT '浼樻儬鍊�',
  `max_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '鏈�澶т紭鎯犻噾棰�',
  `min_order_amount` decimal(10,2) DEFAULT NULL COMMENT '鏈�浣庤鍗曢噾棰�',
  `valid_days` int DEFAULT NULL COMMENT '娉ㄥ唽鍚庢湁鏁堝ぉ鏁�',
  `status` tinyint DEFAULT '1' COMMENT '鐘舵�� 0鍋滅敤 1鍚敤',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  PRIMARY KEY (`id`),
  KEY `idx_ncd_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `template_id` bigint DEFAULT NULL COMMENT '模板ID',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型',
  `channel` tinyint NOT NULL COMMENT '发送渠道: 1=短信 2=APP推送',
  `target_type` tinyint NOT NULL DEFAULT '1' COMMENT '目标类型: 1=单个用户 2=用户分组 3=全部用户',
  `target_value` text NOT NULL COMMENT '目标值手机号或用户ID列表/分群ID，JSON数组',
  `target_count` int NOT NULL DEFAULT '0' COMMENT '目标数量',
  `content` text NOT NULL COMMENT '实际发送内容',
  `send_time` datetime DEFAULT NULL COMMENT '定时发送时间，NULL表示立即发送',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0=待发送 1=发送中 2=成功 3=失败 4=部分成功',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '失败',
  `fail_reason` text COMMENT '失败原因汇总',
  `ext_data` varchar(500) DEFAULT NULL COMMENT '扩展数据JSON',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建用户',
  `update_user` bigint DEFAULT NULL COMMENT '更新',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_send_time` (`send_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知发送记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '模板编码（如SMS_CODE）',
  `channel` tinyint NOT NULL DEFAULT '1' COMMENT '通知渠道: 1=短信 2=APP推送 3=短信+APP推送',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型: ORDER_NOTICE/PROMOTION/VERIFY_CODE/SYSTEM/JOB_NOTICE',
  `title` varchar(200) DEFAULT NULL COMMENT '推送标题(APP推送用)',
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_bin COMMENT '打印内容',
  `param_list` varchar(500) DEFAULT NULL COMMENT '参数列表JSON，如 ["orderNo","amount"]',
  `sign_name` varchar(50) DEFAULT NULL COMMENT '短信签名',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1=启用 0=停用',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建用户',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_biz` (`tenant_id`,`biz_type`),
  KEY `idx_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知模板';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `operator_id` bigint DEFAULT NULL COMMENT '鎿嶄綔浜篒D',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔浜哄鍚�',
  `operator_ip` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔浜篒P',
  `module` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔妯″潡',
  `operation_type` varchar(20) DEFAULT NULL COMMENT '鎿嶄綔绫诲瀷',
  `table_name` varchar(50) DEFAULT NULL COMMENT '涓氬姟琛ㄥ悕',
  `biz_id` bigint DEFAULT NULL COMMENT '涓氬姟璁板綍ID',
  `description` varchar(500) DEFAULT NULL COMMENT '鎿嶄綔鎻忚堪',
  `old_value` text COMMENT '鍙樻洿鍓嶅��(JSON)',
  `new_value` text COMMENT '鍙樻洿鍚庡��(JSON)',
  `request_url` varchar(500) DEFAULT NULL COMMENT '璇锋眰URL',
  `request_method` varchar(10) DEFAULT NULL COMMENT '璇锋眰鏂规硶',
  `request_params` text COMMENT '璇锋眰鍙傛暟(JSON)',
  `duration` bigint DEFAULT NULL COMMENT '鎵ц鏃堕暱(姣)',
  `is_success` int NOT NULL DEFAULT '0' COMMENT '鏄惁鎴愬姛:0澶辫触 1鎴愬姛',
  `error_msg` varchar(1000) DEFAULT NULL COMMENT '閿欒淇℃伅',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_operation_log_tenant` (`tenant_id`),
  KEY `idx_operation_log_operator` (`operator_id`),
  KEY `idx_operation_log_module` (`module`),
  KEY `idx_operation_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(50) NOT NULL COMMENT '鍚嶇О',
  `order_id` bigint NOT NULL COMMENT '璁㈠崟id',
  `dish_id` bigint DEFAULT NULL COMMENT '鑿滃搧id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '濂梚d',
  `dish_flavor` varchar(50) DEFAULT NULL COMMENT '鍙ｅ懗',
  `number` int NOT NULL DEFAULT '1' COMMENT '鏁伴噺',
  `amount` decimal(10,2) NOT NULL COMMENT '鍗曚环',
  `remark` varchar(255) DEFAULT NULL COMMENT '璁㈠崟鏄庣粏澶囨敞',
  `image` varchar(255) DEFAULT NULL COMMENT '鍥剧墖',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_order_detail_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL COMMENT '涓婚敭',
  `number` varchar(50) DEFAULT NULL COMMENT '璁㈠崟',
  `status` int NOT NULL DEFAULT '1' COMMENT '璁㈠崟鐘�',
  `user_id` bigint DEFAULT NULL COMMENT '鐢ㄦ埛id',
  `address_book_id` bigint DEFAULT NULL COMMENT '鍦板潃id',
  `order_time` datetime DEFAULT NULL COMMENT '涓嬪崟鏃堕棿',
  `checkout_time` datetime DEFAULT NULL COMMENT '缁撹处鏃堕棿',
  `pay_method` int DEFAULT NULL COMMENT '鏀绘柟寮�',
  `amount` decimal(10,2) NOT NULL COMMENT '瀹炴敹閲�',
  `delivery_fee` decimal(10,2) DEFAULT NULL COMMENT '閰嶉�佽垂锛堝鍗栧崟鐙珛瀛樺偍锛屽爞椋熶负0锛�',
  `full_reduction_amount` decimal(10,2) DEFAULT '0.00' COMMENT '婊″噺浼樻儬閲戦锛堟弧鍑忔椿鍔ㄦ墸鍑忥紝鏈韩鍙椾负0锛�',
  `new_customer_discount_amount` decimal(10,2) DEFAULT '0.00' COMMENT '鏂板绔嬪噺閲戦锛堟柊瀹㈡椿鍔ㄦ墸鍑忥紝鏈韩鍙椾负0锛�',
  `remark` varchar(100) DEFAULT NULL COMMENT '澶囨敞',
  `internal_remark` varchar(500) DEFAULT NULL COMMENT '鍐呴儴澶囨敞锛堜粎鍚庡彴鍙锛�',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT '鍙栨秷/鎷掑崟鍘熷洜锛圥0-5 鍥炴墽锛岄【瀹㈢?鍙??锛涗笉鍐嶈?鐩?remark锛',
  `pickup_code` varchar(16) DEFAULT NULL COMMENT '鍙栭?鐮侊紙娲惧崟/鎶㈠崟鏃剁敓鎴愶紝楠戞墜鍙栭?椤绘牎楠岋級',
  `expect_delivery_time` varchar(20) DEFAULT NULL COMMENT '棰勯�佽揪鏃堕棿',
  `user_name` varchar(50) DEFAULT NULL COMMENT '鐢ㄦ埛',
  `phone` varchar(255) DEFAULT NULL COMMENT '鎵嬫満',
  `address` varchar(255) DEFAULT NULL COMMENT '鍦板潃',
  `consignee` varchar(50) DEFAULT NULL COMMENT '鏀惰揣',
  `dining_type` varchar(20) DEFAULT 'OUTSIDE' COMMENT '鐢ㄧ被鍨�',
  `table_id` bigint DEFAULT NULL COMMENT '鍫傛鍙癐D',
  `table_name` varchar(32) DEFAULT NULL COMMENT '鍫傛鍙板悕绉�',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '骞傜瓑',
  `stock_refunded` int DEFAULT '0' COMMENT '宸插簱瀛樻暟閲�',
  `used_coupon_id` bigint DEFAULT NULL COMMENT '浼樻儬鍒窱D',
  `rider_id` bigint DEFAULT NULL COMMENT '閰嶉�侀獞鎵婭D锛堝簵闀挎淳鍗�/楠戞墜鎶㈠崟鍚庡啓鍏ワ級',
  `dispatch_time` datetime DEFAULT NULL COMMENT '娲惧崟/鎶㈠崟鏃堕棿锛堣秴鏃跺洖娴佸垽鏂級',
  `platform_type` varchar(32) DEFAULT NULL COMMENT '骞冲彴鏉ユ簮',
  `platform_order_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴璁㈠崟',
  `platform_shop_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴闂ㄥ簵ID',
  `platform_raw` longtext COMMENT '骞冲彴鍘熻鍗旿SON',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓',
  `update_user` bigint DEFAULT NULL COMMENT '淇�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄愬垹闄�',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `version` int NOT NULL DEFAULT '0' COMMENT '涔愰攣鐗堟湉',
  `master_order_id` bigint DEFAULT NULL COMMENT '鐖惰鍗旾D锛圓A鍒嗚处鏃舵寚鍚戜富璁㈠崟锛�',
  `split_count` int DEFAULT NULL COMMENT '鍒嗚处浠芥暟锛圓A鍒嗚处璁板綍鎷嗗垎鏁伴噺锛�',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_orders_platform` (`tenant_id`,`platform_type`,`platform_order_id`),
  KEY `idx_order_user` (`user_id`,`order_time`),
  KEY `idx_order_number` (`number`),
  KEY `idx_order_status` (`status`),
  KEY `idx_order_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `other_cost` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(100) DEFAULT NULL COMMENT '鎴愭湰鍚嶇О',
  `cost_type` int DEFAULT NULL COMMENT '鎴愭湰绫诲瀷 1-绉熼噾/2-姘寸數/3-璁�/4-鑰楁潗/5-钀ラ攢/6-鍏朵粬',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '鎴愭湰閲�',
  `cost_date` datetime DEFAULT NULL COMMENT '鎴愭湰鏃ユ湡',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_channel_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '配置名称',
  `channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '渠道 WECHAT/ALIPAY',
  `wx_app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '微信appId',
  `wx_mch_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '微信商户号',
  `wx_api_v3_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '微信APIv3密钥(加密)',
  `wx_mch_cert_serial_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '商户证书序列号',
  `wx_mch_private_key` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '商户私钥(加密)',
  `wx_public_key_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '微信支付公钥ID',
  `wx_public_key` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '微信支付公钥',
  `ali_app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '支付宝APPID',
  `ali_private_key` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '支付宝应用私钥(加密)',
  `ali_public_key` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '支付宝公钥',
  `pay_notify_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '支付回调地址',
  `refund_notify_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '退款回调地址',
  `enabled` int NOT NULL DEFAULT '1' COMMENT '启用 0停 1启',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0未删 1已删',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_channel` (`tenant_id`,`channel`,`is_deleted`) USING BTREE,
  KEY `idx_pcc_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='支付渠道配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '业务订单id',
  `biz_type` varchar(20) NOT NULL DEFAULT 'ORDER' COMMENT '业务类型 ORDER/RECHARGE',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  `trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统交易号',
  `channel_trade_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '通道交易号',
  `channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付通道 ALIPAY/WECHAT/UNIONPAY',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '支付状态（PENDING-待支付 SUCCESS-支付成功 FAIL-支付失败 REFUND-已退款）',
  `paid_time` datetime DEFAULT NULL COMMENT '支付时间',
  `notify_time` datetime DEFAULT NULL COMMENT '回调时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL,
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_trade_no` (`trade_no`) USING BTREE,
  KEY `idx_order` (`order_id`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE,
  KEY `idx_channel_trade` (`channel_trade_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='支付订单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `permission_name` varchar(50) NOT NULL COMMENT '鏉冮檺鍚嶇О',
  `permission_key` varchar(100) NOT NULL COMMENT '鏉冮檺鏍囪瘑',
  `permission_type` int NOT NULL DEFAULT '1' COMMENT '鏉冮檺绫诲瀷 1:鑿滃崟 2:鎸夐挳 3:鏁版嵁',
  `parent_id` bigint DEFAULT '0' COMMENT '鐖舵潈闄怚D锛�0=椤剁骇锛�',
  `route_path` varchar(200) DEFAULT NULL COMMENT '璺敱璺緞',
  `icon` varchar(100) DEFAULT NULL COMMENT '鑿滃崟鍥炬爣',
  `sort` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭',
  `status` int NOT NULL DEFAULT '1' COMMENT '鐘舵�� 0:绂佺敤 1:鍚敤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_permission_key` (`permission_key`),
  KEY `idx_permission_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `platform_type` varchar(32) NOT NULL COMMENT '骞冲彴绫诲瀷 MEITUAN/ELEME/DOUYIN/SELF/OTHER',
  `platform_name` varchar(128) DEFAULT NULL COMMENT '骞冲彴灞曠ず鍚嶇О',
  `shop_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴渚ч棬搴桰D',
  `app_key` varchar(512) DEFAULT NULL COMMENT '搴旂敤鏍囪瘑(鍔犲瘑)',
  `app_secret` varchar(512) DEFAULT NULL COMMENT '搴旂敤瀵嗛挜(鍔犲瘑)',
  `access_token` varchar(512) DEFAULT NULL COMMENT '璁块棶浠ょ墝(鍔犲瘑)',
  `enabled` int NOT NULL DEFAULT '1' COMMENT '鏄愬悢 0鍋滅敤 1鍚�',
  `sync_scope` int NOT NULL DEFAULT '1' COMMENT '鍚岃寖鍥翠綅鏍� 1璁㈠崟2鍟嗗搧4搴撳瓨8钀ヤ笟鐘�',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_platform_type_shop` (`platform_type`,`shop_id`),
  KEY `idx_platform_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_reconcile_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `platform_type` varchar(32) NOT NULL COMMENT '骞冲彴绫诲瀷',
  `reconcile_date` date NOT NULL COMMENT '瀵硅处鏃ユ湡',
  `begin_time` datetime NOT NULL COMMENT '瀵硅处濮嬫椂',
  `end_time` datetime NOT NULL COMMENT '瀵硅处缁撴潫鏃堕棿',
  `total_platform_count` int NOT NULL DEFAULT '0' COMMENT '骞冲彴渚ц鍗曟暟',
  `total_local_count` int NOT NULL DEFAULT '0' COMMENT '鏈湴璁㈠崟鏁�',
  `match_count` int NOT NULL DEFAULT '0' COMMENT '鍖归厤鎴愬姛',
  `missing_local_count` int NOT NULL DEFAULT '0' COMMENT '骞冲彴鏈夋湰鍦版棤',
  `missing_platform_count` int NOT NULL DEFAULT '0' COMMENT '鏈滄湁骞冲彴鏃�',
  `status` int NOT NULL DEFAULT '0' COMMENT '鐘� 0=杩� 1=瀹屾垚 2=澶辫触',
  `error_message` varchar(512) DEFAULT NULL COMMENT '閿欎俊鎭�',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_reconcile_task_date_platform` (`reconcile_date`,`platform_type`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `platform_type` varchar(32) NOT NULL COMMENT '骞冲彴绫诲瀷',
  `platform_order_id` varchar(128) DEFAULT NULL COMMENT '骞冲彴璁㈠崟ID',
  `local_order_id` bigint DEFAULT NULL COMMENT '鏈滆鍗旾D',
  `action` varchar(32) NOT NULL COMMENT '鍔ㄤ綔 PULL/ACCEPT/REJECT',
  `direction` varchar(16) NOT NULL DEFAULT 'IN' COMMENT '鏂瑰悜 IN=鎷夊崟 OUT=鍥炰紶',
  `request_body` text COMMENT '璇锋眰鍐�',
  `response_body` text COMMENT '鍝嶅簲鍐�',
  `status` int NOT NULL DEFAULT '0' COMMENT '缁撴灉 0=鎴愬姛 1=澶辫触',
  `error_message` varchar(512) DEFAULT NULL COMMENT '閿欎俊鎭�',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '閲嶈瘯娆℃暟',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_sync_log_platform_order` (`platform_type`,`platform_order_id`),
  KEY `idx_sync_log_local_order` (`local_order_id`),
  KEY `idx_sync_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `member_id` bigint DEFAULT NULL COMMENT '浼氬憳ID',
  `type` varchar(20) DEFAULT NULL COMMENT '绫诲瀷',
  `points` int DEFAULT NULL COMMENT '绉堟暟閲�',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '鍏宠仈涓氬姟绫诲瀷',
  `biz_id` bigint DEFAULT NULL COMMENT '鍏宠仈涓氬姟ID',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `expire_time` datetime DEFAULT NULL COMMENT '绉垎杩囨湡鏃堕棿',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_points_biz` (`tenant_id`,`biz_type`,`biz_id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `price_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `material_id` bigint NOT NULL COMMENT '鐗╂枡ID',
  `old_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鏃т环鏍�',
  `new_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鏂颁环鏍�',
  `change_reason` varchar(255) DEFAULT '' COMMENT '鍙樺姩鍘熷洜',
  `operator_id` bigint NOT NULL DEFAULT '0' COMMENT '鎿嶄綔浜篒D',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_price_history_tenant_id` (`tenant_id`),
  KEY `idx_price_history_material_id` (`material_id`),
  KEY `idx_price_history_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `print_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `store_code` varchar(50) NOT NULL DEFAULT '',
  `order_id` bigint DEFAULT NULL,
  `task_type` varchar(20) NOT NULL DEFAULT 'BILL',
  `content` text,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `terminal_id` bigint DEFAULT NULL,
  `terminal_code` varchar(64) NOT NULL DEFAULT '',
  `error_msg` varchar(500) NOT NULL DEFAULT '',
  `retry_count` int NOT NULL DEFAULT '0',
  `created_time` datetime DEFAULT NULL,
  `pulled_time` datetime DEFAULT NULL,
  `done_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_print_task_status` (`status`),
  KEY `idx_print_task_order` (`order_id`),
  KEY `idx_print_task_terminal` (`terminal_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印任务';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `print_terminal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `store_code` varchar(50) NOT NULL DEFAULT '',
  `terminal_code` varchar(64) NOT NULL DEFAULT '',
  `token` varchar(64) NOT NULL DEFAULT '',
  `name` varchar(100) NOT NULL DEFAULT '',
  `printer_name` varchar(200) NOT NULL DEFAULT '',
  `paper_size` varchar(20) NOT NULL DEFAULT '80mm',
  `print_types` varchar(50) NOT NULL DEFAULT 'BILL',
  `status` tinyint NOT NULL DEFAULT '0',
  `last_heartbeat` datetime DEFAULT NULL,
  `client_version` varchar(30) NOT NULL DEFAULT '',
  `created_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_terminal_code` (`terminal_code`),
  KEY `idx_print_terminal_tenant` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `printer_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户id',
  `store_id` bigint DEFAULT NULL COMMENT '门店id',
  `name` varchar(50) NOT NULL COMMENT '打印机名称',
  `type` varchar(20) NOT NULL COMMENT '连接类型 USB/TCP/CLOUD/BLUETOOTH',
  `brand` varchar(20) DEFAULT NULL COMMENT '品牌 佳博/芯烨/商米',
  `device_id` varchar(100) DEFAULT NULL COMMENT '设备标识 MAC/SN',
  `system_printer_name` varchar(200) DEFAULT NULL COMMENT '系统打印机名称（Windows下为驱动名称）',
  `ip_address` varchar(15) DEFAULT NULL COMMENT 'IP地址',
  `port` int DEFAULT NULL COMMENT '端口',
  `paper_size` varchar(10) DEFAULT '58mm' COMMENT '纸张规格 58mm/80mm',
  `print_types` varchar(100) DEFAULT NULL COMMENT '打印类型',
  `status` int DEFAULT '1' COMMENT '状态 0禁用 1启用',
  `sort` int DEFAULT '0' COMMENT '排序',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL,
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `printer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint DEFAULT NULL COMMENT '订单id',
  `print_type` varchar(20) NOT NULL COMMENT '打印类型',
  `printer_id` bigint DEFAULT NULL COMMENT '打印机id',
  `content` text COMMENT '打印内容',
  `status` int DEFAULT '0' COMMENT '状态 0失败 1成功',
  `error_msg` varchar(255) DEFAULT NULL COMMENT '错误信息',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `profit_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `analysis_date` date DEFAULT NULL COMMENT '鍒嗘瀽鏃ユ湡',
  `total_revenue` decimal(12,2) DEFAULT NULL COMMENT '鎬昏惀',
  `food_cost` decimal(12,2) DEFAULT NULL COMMENT '椋熸潗鎴愭湰',
  `labor_cost` decimal(12,2) DEFAULT NULL COMMENT '浜哄伐鎴愭湰',
  `other_cost` decimal(12,2) DEFAULT NULL COMMENT '鍏朵粬鎴愭湰',
  `total_cost` decimal(12,2) DEFAULT NULL COMMENT '鎬绘垚',
  `gross_profit` decimal(12,2) DEFAULT NULL COMMENT '姣涘埄',
  `gross_profit_rate` decimal(10,4) DEFAULT NULL COMMENT '姣涘埄(%)',
  `operating_expense` decimal(12,2) DEFAULT NULL COMMENT '杩愯惀璐圭敤',
  `net_profit` decimal(12,2) DEFAULT NULL COMMENT '鍒╂鼎',
  `net_profit_rate` decimal(10,4) DEFAULT NULL COMMENT '鍒╃巼(%)',
  `order_count` int DEFAULT NULL COMMENT '璁㈠崟',
  `customer_count` int DEFAULT NULL COMMENT '瀹㈡埛',
  `average_order_value` decimal(10,2) DEFAULT NULL COMMENT '瀹㈠崟',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '璁㈠崟缂栧彿',
  `supplier_id` bigint DEFAULT NULL COMMENT '渚涘簲鍟咺D',
  `total_amount` decimal(10,2) DEFAULT NULL COMMENT '鎬婚噾棰�',
  `status` varchar(32) DEFAULT NULL COMMENT '鐘舵��',
  `operator` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔鍛�',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `voucher_images` varchar(1000) DEFAULT NULL COMMENT '鍑瘉鍥剧墖锛堥�楀彿鍒嗛殧锛屾渶澶�5寮狅級',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  `version` int NOT NULL DEFAULT '0' COMMENT '止姹�',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_tenant_id` (`tenant_id`),
  KEY `idx_purchase_order_supplier_id` (`supplier_id`),
  KEY `idx_purchase_order_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `purchase_order_id` bigint DEFAULT NULL COMMENT '閲囪喘璁㈠崟ID',
  `material_id` bigint DEFAULT NULL COMMENT '鐗╂枡ID',
  `qty` decimal(10,2) DEFAULT NULL COMMENT '鏁伴噺',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '鍗曚环',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '閲戦',
  `received_qty` decimal(10,2) DEFAULT NULL COMMENT '鏀惰揣鏁伴噺',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_detail_tenant_id` (`tenant_id`),
  KEY `idx_purchase_order_detail_purchase_order_id` (`purchase_order_id`),
  KEY `idx_purchase_order_detail_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recharge_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `member_id` bigint DEFAULT NULL COMMENT '浼氬憳ID',
  `user_id` bigint DEFAULT NULL COMMENT '褰掑睘鐢ㄦ埛ID锛圕绔嚜鍔╁厖鍊煎啑浣欙級',
  `recharge_no` varchar(64) DEFAULT NULL COMMENT '鍏呭�煎崟鍙凤紙涓氬姟鍞竴锛�',
  `status` varchar(20) DEFAULT 'SUCCESS' COMMENT '鐘舵�� PENDING/SUCCESS/CANCELLED',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '鍏呴噾',
  `gift_amount` decimal(10,2) DEFAULT '0.00' COMMENT '璧犻噾',
  `payment_method` varchar(20) DEFAULT NULL COMMENT '娓犻亾 WECHAT/ALIPAY/CASH锛堥鐣欏湪绾挎敮浠橈級',
  `trade_no` varchar(64) DEFAULT NULL COMMENT '娓犻亾浜ゆ槗鍙凤紙棰勭暀鍦ㄧ嚎鏀粯鍥炲～锛�',
  `confirm_employee_id` bigint DEFAULT NULL COMMENT '纭鍒拌处鍛樺伐ID',
  `confirm_time` datetime DEFAULT NULL COMMENT '纭鍒拌处鏃堕棿',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recharge_no` (`recharge_no`),
  KEY `idx_rr_member_status` (`member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recommendation_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `recommend_type` tinyint NOT NULL COMMENT '推荐类型 1:菜品推荐 2:套餐推荐 3:新品尝鲜',
  `dish_ids` text NOT NULL COMMENT '推荐菜品/套餐ID列表，JSON数组格式',
  `algo_name` varchar(32) NOT NULL COMMENT '算法名称',
  `score` decimal(3,2) NOT NULL DEFAULT '0.00' COMMENT '推荐评分0.00~1.00',
  `expire_time` datetime NOT NULL COMMENT '缓存过期时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_recommend` (`user_id`,`recommend_type`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE,
  KEY `idx_expire` (`expire_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='推荐结果缓存';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recommendation_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `recommend_cache_id` bigint DEFAULT NULL COMMENT '关联推荐缓存ID',
  `dish_id` bigint NOT NULL COMMENT '菜品/套餐ID',
  `feedback_type` tinyint NOT NULL COMMENT '反馈类型 1:点击 2:收藏 3:加购 4:下单 5:不感兴趣',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_dish` (`dish_id`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='推荐反馈';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_statement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `statement_no` varchar(50) DEFAULT NULL COMMENT '瀵硅处鍗曠紪',
  `statement_date` date DEFAULT NULL COMMENT '瀵硅处鏃ユ湡',
  `platform` varchar(20) DEFAULT 'all' COMMENT '骞冲彴 all/wechat/alipay/bank',
  `system_amount` decimal(12,2) DEFAULT NULL COMMENT '绯荤粺閲�',
  `platform_amount` decimal(12,2) DEFAULT NULL COMMENT '骞冲彴閲�',
  `difference_amount` decimal(12,2) DEFAULT NULL COMMENT '宸紓閲戦',
  `order_count` int DEFAULT NULL COMMENT '璁㈠崟',
  `refund_amount` decimal(12,2) DEFAULT NULL COMMENT '娆鹃噾',
  `refund_count` int DEFAULT NULL COMMENT '娆炬暟',
  `fee_amount` decimal(10,2) DEFAULT NULL COMMENT '鎵嬬画',
  `net_amount` decimal(12,2) DEFAULT NULL COMMENT '鍑�棰�',
  `status` int DEFAULT '0' COMMENT '鐘� 0- 1-宸� 2-鏈夊樊',
  `reconcile_time` datetime DEFAULT NULL COMMENT '瀵硅处鏃堕棿',
  `reconcile_user_id` bigint DEFAULT NULL COMMENT '瀵硅处浜篒D',
  `reconcile_user_name` varchar(50) DEFAULT NULL COMMENT '瀵硅处浜�',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `payment_order_id` bigint NOT NULL COMMENT '支付订单id',
  `order_id` bigint DEFAULT NULL COMMENT '业务订单ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `refund_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '退款单号',
  `amount` decimal(10,2) NOT NULL COMMENT '退款金额',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '退款原因',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '退款状态（PENDING-待退款 SUCCESS-退款成功 FAIL-退款失败）',
  `refund_type` int DEFAULT NULL COMMENT '售后类型：1=整单退款 2=部分退款',
  `apply_user_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观锁版本号',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  `audit_user_id` bigint DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_refund_no` (`refund_no`) USING BTREE,
  KEY `idx_payment` (`payment_order_id`) USING BTREE,
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='退款记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `region` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(50) NOT NULL COMMENT '鍦板尯鍚嶇О',
  `code` varchar(20) DEFAULT NULL COMMENT '琛屾斂鍖哄垝浠ｇ爜',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '鐖剁骇ID锛�0涓虹渷浠�',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '灞傜骇锛�1鐪� 2甯� 3鍖�/鍘�',
  `sort` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(100) DEFAULT NULL,
  `avatar` varchar(200) DEFAULT NULL,
  `current_longitude` decimal(12,8) DEFAULT NULL,
  `current_latitude` decimal(12,8) DEFAULT NULL,
  `status` int DEFAULT '0',
  `current_order_count` int DEFAULT '0',
  `total_order_count` int DEFAULT '0',
  `rating` decimal(3,1) DEFAULT '5.0',
  `last_location_time` datetime DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `rider_id` bigint DEFAULT NULL COMMENT '楠戞墜id',
  `withdrawable_balance` decimal(12,2) DEFAULT '0.00' COMMENT '鍙?彁鐜颁綑棰',
  `frozen_balance` decimal(12,2) DEFAULT '0.00' COMMENT '鍐荤粨涓?噾棰',
  `total_income` decimal(12,2) DEFAULT '0.00' COMMENT '绱??鏀跺叆',
  `total_withdrawn` decimal(12,2) DEFAULT '0.00' COMMENT '绱??宸叉彁鐜',
  `version` int DEFAULT '0' COMMENT '涔愯?閿',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rider_tenant` (`rider_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楠戞墜缁撶畻璐︽埛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `order_id` bigint DEFAULT NULL COMMENT '璁㈠崟id',
  `user_id` bigint DEFAULT NULL COMMENT '璇勪环鐢ㄦ埛id',
  `user_name` varchar(50) DEFAULT NULL COMMENT '璇勪环鐢ㄦ埛鍚',
  `rider_id` bigint DEFAULT NULL COMMENT '楠戞墜id',
  `rider_name` varchar(50) DEFAULT NULL COMMENT '楠戞墜濮撳悕',
  `star_rating` int DEFAULT NULL COMMENT '璇勫垎1-5',
  `content` varchar(500) DEFAULT NULL COMMENT '璇勪环鍐呭?',
  `tags` varchar(255) DEFAULT NULL COMMENT '鏍囩?JSON鏁扮粍',
  `anonymous` int DEFAULT '0' COMMENT '0瀹炲悕1鍖垮悕',
  `status` int DEFAULT '1' COMMENT '0寰呭?1閫氳繃2鎷掔粷',
  `reply_content` varchar(500) DEFAULT NULL COMMENT '鍟嗗?鍥炲?',
  `reply_time` datetime DEFAULT NULL COMMENT '鍥炲?鏃堕棿',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_rider_del` (`order_id`,`rider_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楠戞墜閰嶉?鏈嶅姟璇勪环';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_exception_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `order_id` bigint DEFAULT NULL COMMENT '鍏宠仈璁㈠崟id',
  `order_number` varchar(50) DEFAULT NULL COMMENT '璁㈠崟鍙',
  `rider_id` bigint DEFAULT NULL COMMENT '涓婃姤楠戞墜id',
  `rider_name` varchar(50) DEFAULT NULL COMMENT '涓婃姤楠戞墜濮撳悕',
  `exception_type` int DEFAULT NULL COMMENT '1鑱旂郴涓嶄笂椤惧?2鍟嗗搧鐮存崯3鍦板潃鏈夎?4椤惧?鎷掓敹5鐢宠?杞?崟6鍏朵粬',
  `description` varchar(500) DEFAULT NULL COMMENT '寮傚父鎻忚堪',
  `status` int DEFAULT '0' COMMENT '0寰呭?鐞?宸插?鐞?宸茶浆鍗?宸插叧闂',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '澶勭悊澶囨敞',
  `handler_id` bigint DEFAULT NULL COMMENT '澶勭悊浜篿d',
  `handle_time` datetime DEFAULT NULL COMMENT '澶勭悊鏃堕棿',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楠戞墜寮傚父宸ュ崟';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_income_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `rider_id` bigint DEFAULT NULL COMMENT '楠戞墜id',
  `order_id` bigint DEFAULT NULL COMMENT '璁㈠崟id锛堝箓绛夐敭锛',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '鍏ヨ处閲戦?',
  `status` int DEFAULT '1' COMMENT '鐘舵?',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楠戞墜鏀跺叆娴佹按';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_location_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rider_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `longitude` decimal(12,8) NOT NULL,
  `latitude` decimal(12,8) NOT NULL,
  `speed` decimal(5,2) DEFAULT NULL,
  `direction` decimal(5,2) DEFAULT NULL,
  `record_time` datetime NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `rider_id` bigint DEFAULT NULL COMMENT '鎺ユ敹楠戞墜id',
  `type` int DEFAULT NULL COMMENT '1娲惧崟鎻愰啋2鍌?崟鎻愰啋3寮傚父澶勭悊4绯荤粺鍏?憡5鍏朵粬',
  `title` varchar(100) DEFAULT NULL COMMENT '娑堟伅鏍囬?',
  `content` varchar(500) DEFAULT NULL COMMENT '娑堟伅鍐呭?',
  `biz_id` bigint DEFAULT NULL COMMENT '鍏宠仈涓氬姟id锛堣?鍗昳d锛',
  `is_read` int DEFAULT '0' COMMENT '0鏈??1宸茶?',
  `read_time` datetime DEFAULT NULL COMMENT '闃呰?鏃堕棿',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_user` bigint DEFAULT NULL,
  `update_user` bigint DEFAULT NULL,
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_rider_read` (`rider_id`,`is_read`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楠戞墜娑堟伅';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_remember_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rider_id` bigint NOT NULL COMMENT '骑手ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记住登录随机令牌',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_token` (`token`) USING BTREE,
  KEY `idx_rider` (`rider_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='骑手记住登录令牌';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rider_withdrawal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `rider_id` bigint DEFAULT NULL COMMENT '楠戞墜id',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '鎻愮幇閲戦?',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `apply_time` datetime DEFAULT NULL COMMENT '鐢宠?鏃堕棿',
  `review_time` datetime DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `reviewer_id` bigint DEFAULT NULL COMMENT '瀹℃牳浜篿d',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '瀹℃牳浜哄悕绉',
  `remark` varchar(255) DEFAULT NULL COMMENT '澶囨敞/鎷掔粷鍘熷洜',
  `version` int DEFAULT '0' COMMENT '涔愯?閿',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='楠戞墜鎻愮幇鐢宠?';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID锛圢ULL=鍏ㄥ眬瑙掕壊锛�',
  `role_name` varchar(50) NOT NULL COMMENT '瑙掕壊鍚嶇О',
  `role_key` varchar(50) NOT NULL COMMENT '瑙掕壊鏉冮檺瀛楃涓�',
  `description` varchar(200) DEFAULT NULL COMMENT '瑙掕壊鎻忚堪',
  `sort` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '鐘舵�� 0:绂佺敤 1:鍚敤',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `role_id` bigint NOT NULL COMMENT '瑙掕壊ID',
  `permission_id` bigint NOT NULL COMMENT '鏉冮檺ID',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_role_permission_role` (`role_id`),
  KEY `idx_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `category_id` bigint NOT NULL COMMENT '鑿滃搧鍒嗙被id',
  `name` varchar(64) NOT NULL COMMENT '濂楅鍚嶇О',
  `price` decimal(10,2) NOT NULL COMMENT '濂楅浠锋牸',
  `code` varchar(64) DEFAULT NULL COMMENT '缂栫爜',
  `image` varchar(255) DEFAULT NULL COMMENT '鍥剧墖',
  `description` varchar(400) DEFAULT NULL COMMENT '鎻忚堪淇℃伅',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '鐘舵�� 0:鍋滅敤 1:鍚敤',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint NOT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint NOT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  PRIMARY KEY (`id`),
  KEY `idx_setmeal_category` (`category_id`),
  KEY `idx_setmeal_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `setmeal_id` bigint NOT NULL COMMENT '濂楅id',
  `dish_id` bigint NOT NULL COMMENT '鑿滃搧id',
  `name` varchar(64) NOT NULL COMMENT '鑿滃搧鍚嶇О锛堝啑浣欙級',
  `price` decimal(10,2) NOT NULL COMMENT '鑿滃搧鍘熶环锛堝啑浣欙級',
  `copies` int NOT NULL DEFAULT '1' COMMENT '浠芥暟',
  `sort` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint NOT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint NOT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '鏄惁鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_setmeal_dish_setmeal` (`setmeal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(50) NOT NULL COMMENT '鍚嶇О',
  `user_id` bigint NOT NULL COMMENT '涓婚敭',
  `dish_id` bigint DEFAULT NULL COMMENT '鑿滃搧id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '濂楅id',
  `dish_flavor` varchar(50) DEFAULT NULL COMMENT '鍙ｅ懗',
  `number` int NOT NULL DEFAULT '1' COMMENT '鏁伴噺',
  `amount` decimal(10,2) NOT NULL COMMENT '閲戦',
  `image` varchar(255) DEFAULT NULL COMMENT '鍥剧墖',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_cart_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_check` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `check_no` varchar(50) DEFAULT NULL COMMENT '鐩樼偣鍗曞彿',
  `status` varchar(32) DEFAULT NULL COMMENT '鐘舵��',
  `total_diff_amount` decimal(10,2) DEFAULT NULL COMMENT '鎬诲樊寮傞噾棰�',
  `operator` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔鍛�',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `voucher_images` varchar(1000) DEFAULT NULL COMMENT '鍑瘉鍥剧墖锛堥�楀彿鍒嗛殧锛屾渶澶�5寮狅級',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_stock_check_tenant_id` (`tenant_id`),
  KEY `idx_stock_check_check_no` (`check_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_check_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `check_id` bigint DEFAULT NULL COMMENT '鐩樼偣ID',
  `material_id` bigint DEFAULT NULL COMMENT '鐗╂枡ID',
  `book_qty` decimal(10,2) DEFAULT NULL COMMENT '璐﹂潰鏁伴噺',
  `actual_qty` decimal(10,2) DEFAULT NULL COMMENT '瀹為檯鏁伴噺',
  `diff_qty` decimal(10,2) DEFAULT NULL COMMENT '宸紓鏁伴噺',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_stock_check_detail_tenant_id` (`tenant_id`),
  KEY `idx_stock_check_detail_check_id` (`check_id`),
  KEY `idx_stock_check_detail_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `material_id` bigint DEFAULT NULL COMMENT '鐗╂枡ID',
  `type` varchar(32) DEFAULT NULL COMMENT '绫诲瀷',
  `qty` decimal(10,2) DEFAULT NULL COMMENT '鏁伴噺',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '鍗曚环',
  `total_amount` decimal(10,2) DEFAULT NULL COMMENT '鎬婚噾棰�',
  `biz_id` bigint DEFAULT NULL COMMENT '涓氬姟ID',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `voucher_images` varchar(1000) DEFAULT NULL COMMENT '鍑瘉鍥剧墖锛堥�楀彿鍒嗛殧锛屾渶澶�5寮狅級',
  `operator` varchar(50) DEFAULT NULL COMMENT '鎿嶄綔鍛�',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_stock_record_tenant_id` (`tenant_id`),
  KEY `idx_stock_record_material_id` (`material_id`),
  KEY `idx_stock_record_biz_id` (`biz_id`),
  KEY `idx_stock_record_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '门店名称',
  `address` varchar(255) NOT NULL COMMENT '门店地址',
  `phone` varchar(20) DEFAULT NULL COMMENT 'ϵ绰',
  `business_hours` varchar(100) DEFAULT NULL COMMENT '营业时间',
  `logo` varchar(255) DEFAULT NULL COMMENT '门店Logo',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '状态 0:停业 1:营业',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '是否删除',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛/闂ㄥ簵ID',
  `config_key` varchar(100) DEFAULT NULL COMMENT '閰嶇疆閿�',
  `config_value` varchar(500) DEFAULT NULL COMMENT '閰嶇疆鍊�',
  `config_type` int DEFAULT NULL COMMENT '閰嶇疆绫诲瀷 1:鍔熻兘閰嶇疆 2:杩愯惀鍙傛暟 3:鏄剧ず璁剧疆 4:鍏朵粬',
  `description` varchar(200) DEFAULT NULL COMMENT '閰嶇疆璇存槑',
  `created_by` bigint DEFAULT NULL COMMENT '閰嶇疆鍒涘缓浜�(鎬婚儴绠＄悊鍛�)',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 0=鏈垹闄� 1=宸插垹闄�',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store_daily_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `summary_date` date DEFAULT NULL COMMENT '缁熻鏃ユ湡',
  `total_orders` int DEFAULT NULL COMMENT '璁㈠崟鎬绘暟',
  `completed_orders` int DEFAULT NULL COMMENT '宸插畬鎴愯鍗曟暟',
  `cancelled_orders` int DEFAULT NULL COMMENT '鍙栨秷璁㈠崟',
  `total_amount` decimal(12,2) DEFAULT NULL COMMENT '璁㈠崟鎬婚噾棰�',
  `actual_amount` decimal(12,2) DEFAULT NULL COMMENT '瀹炴敹閲戦',
  `new_users` int DEFAULT NULL COMMENT '鏂板鐢ㄦ埛',
  `avg_order_amount` decimal(10,2) DEFAULT NULL COMMENT '骞冲潎璁㈠崟閲戦',
  `top_dish_json` varchar(500) DEFAULT NULL COMMENT '鐑攢鑿滃搧TOP10 JSON',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 0=鏈垹闄� 1=宸插垹闄�',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store_employee_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `employee_id` bigint DEFAULT NULL COMMENT '鍛樺伐ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `role_type` int DEFAULT NULL COMMENT '瑙掕壊绫诲瀷 1:搴楅暱 2:鍘ㄥ笀 3:鏈嶅姟鍛� 4:鏀堕摱鍛� 5:閰嶈彍鍛�',
  `permissions` varchar(500) DEFAULT NULL COMMENT '鏉冮檺鍒楄〃 JSON锛屽 ["dish:view","dish:edit","order:view"]',
  `is_active` int DEFAULT NULL COMMENT '鏄惁鐢熸晥 0:鍚� 1:鏄�',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓鐢ㄦ埛',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 0=鏈垹闄� 1=宸插垹闄�',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '鎵�灞炵鎴�/闂ㄥ簵ID',
  `store_code` varchar(50) DEFAULT NULL COMMENT '闂ㄥ簵缂栫爜锛屽锛欱J001銆丼H001',
  `store_type` int DEFAULT NULL COMMENT '闂ㄥ簵绫诲瀷 1:鐩磋惀鎬诲簵 2:鐩磋惀鍒嗗簵 3:鍔犵洘',
  `parent_tenant_id` bigint DEFAULT NULL COMMENT '涓婄骇鎬诲簵tenantId锛孨ULL琛ㄧず鎬诲簵鏈韩',
  `business_hours` varchar(200) DEFAULT NULL COMMENT '钀ヤ笟鏃堕棿锛屽 9:00-22:00',
  `notice` varchar(200) DEFAULT NULL COMMENT '门店公告，C端首页展示',
  `delivery_radius` int DEFAULT NULL COMMENT '閰嶉�佸崐寰�(绫�)',
  `min_delivery_amount` decimal(10,2) DEFAULT NULL COMMENT '鏈�浣庤捣閫侀噾棰�',
  `delivery_fee` decimal(10,2) DEFAULT NULL COMMENT '閰嶉�佽垂',
  `is_delivery_enabled` int DEFAULT NULL COMMENT '鏄惁澶栧崠 0:鍚� 1:鏄�',
  `is_dine_in_enabled` int DEFAULT NULL COMMENT '鏄惁鍫傞 0:鍚� 1:鏄�',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '闂ㄥ簵鑱旂郴',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '诺系缁�',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '缁忓害',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '绾害',
  `pause_order` int NOT NULL DEFAULT '0' COMMENT '鏆傚仠鎺ュ崟 0:姝ｅ父 1:鏆傚仠',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓鐢ㄦ埛',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 0=鏈垹闄� 1=宸插垹闄�',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `store_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `source_tenant_id` bigint DEFAULT NULL COMMENT '鏉ユ簮闂ㄥ簵ID(閫氬父鎸囨�婚儴)',
  `target_tenant_id` bigint DEFAULT NULL COMMENT '鐩爣闂ㄥ簵ID',
  `sync_type` int DEFAULT NULL COMMENT '鍚屾绫诲瀷 1:鑿滃搧鍚屾 2:鍒嗙被鍚屾 3:濂楅鍚屾 4:閰嶇疆鍚屾 5:浼樻儬鍒稿悓姝�',
  `sync_mode` int DEFAULT NULL COMMENT '鍚屾妯″紡 1:鍏ㄩ噺鍚屾 2:澧為噺鍚屾 3:閫夋嫨鎬у悓姝�',
  `sync_status` int DEFAULT NULL COMMENT '鍚屾鐘舵�� 0:杩涜涓� 1:鎴愬姛 2:澶辫触 3:閮ㄥ垎鎴愬姛',
  `sync_count` int DEFAULT NULL COMMENT '鍚屾鏁伴噺',
  `fail_count` int DEFAULT NULL COMMENT '澶辫触鏁伴噺',
  `error_detail` varchar(1000) DEFAULT NULL COMMENT '閿欒璇︽儏',
  `operator_id` bigint DEFAULT NULL COMMENT '鎿嶄綔浜篒D',
  `start_time` datetime DEFAULT NULL COMMENT '鍚屾寮�濮嬫椂闂�',
  `end_time` datetime DEFAULT NULL COMMENT '缁撴潫鏃堕棿',
  `create_time` datetime DEFAULT NULL COMMENT '闂ㄥ簵鍚屾鏃ュ織',
  `update_time` datetime DEFAULT NULL COMMENT '闂ㄥ簵鍚屾鏃ュ織',
  `create_user` bigint DEFAULT NULL COMMENT '闂ㄥ簵鍚屾鏃ュ織',
  `update_user` bigint DEFAULT NULL COMMENT '闂ㄥ簵鍚屾鏃ュ織',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛�0=鏈垹闄わ紝1=宸插垹闄�',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `name` varchar(100) DEFAULT NULL COMMENT '渚涘簲鍟嗗悕绉�',
  `contact` varchar(50) DEFAULT NULL COMMENT '鑱旂郴浜�',
  `phone` varchar(20) DEFAULT NULL COMMENT '系缁�',
  `address` varchar(255) DEFAULT NULL COMMENT '鍦板潃',
  `license_images` varchar(1000) DEFAULT NULL COMMENT '璧勮川鍥剧墖锛堥�楀彿鍒嗛殧锛屾渶澶�5寮狅級',
  `status` int DEFAULT NULL COMMENT '鐘舵��',
  `created_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_supplier_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_settlement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `supplier_id` bigint NOT NULL COMMENT '渚涘簲鍟咺D',
  `period` varchar(20) NOT NULL COMMENT '缁撶畻鍛ㄦ湡',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '鎬婚噾棰�',
  `paid_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '宸蹭粯閲戦',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '鐘舵��',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_supplier_settlement_tenant_id` (`tenant_id`),
  KEY `idx_supplier_settlement_supplier_id` (`supplier_id`),
  KEY `idx_supplier_settlement_period` (`period`),
  KEY `idx_supplier_settlement_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID锛圢ULL=鍏ㄥ眬閰嶇疆锛�',
  `config_key` varchar(100) NOT NULL COMMENT '閰嶇疆閿�',
  `config_value` varchar(500) NOT NULL COMMENT '閰嶇疆鍊�',
  `config_type` int NOT NULL DEFAULT '1' COMMENT '閰嶇疆绫诲瀷 1:鍔熻兘寮�鍏� 2:杩愯惀鍙傛暟 3:鏄剧ず璁剧疆 4:鍏朵粬',
  `description` varchar(200) DEFAULT NULL COMMENT '閰嶇疆璇存槑',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜篒D',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜篒D',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_system_config_key_tenant` (`config_key`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(64) DEFAULT NULL COMMENT '绉熸埛鍚嶇О',
  `phone` varchar(20) DEFAULT NULL COMMENT '鐢佃瘽',
  `address` varchar(255) DEFAULT NULL COMMENT '鍦板潃',
  `contact` varchar(32) DEFAULT NULL COMMENT '鑱旂郴浜�',
  `logo` varchar(255) DEFAULT NULL COMMENT 'Logo鍥剧墖鐩稿璺緞',
  `license_image` varchar(255) DEFAULT NULL COMMENT '钀ヤ笟鎵х収鍥剧墖鐩稿璺緞',
  `package_name` varchar(32) DEFAULT NULL COMMENT '濂楅鍚嶇О',
  `expire_time` datetime DEFAULT NULL COMMENT '濂楅鍒版湡鏃堕棿',
  `password_type` varchar(20) DEFAULT 'MD5' COMMENT '瀵嗙爜鍔犲瘑绫诲瀷',
  `status` int NOT NULL DEFAULT '1' COMMENT '鐘舵�� 0:绂佺敤 1:姝ｅ父',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_user` bigint DEFAULT NULL COMMENT '淇敼浜�',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `urgency_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单号',
  `times` int NOT NULL DEFAULT '1' COMMENT '催单次数',
  `status` varchar(16) NOT NULL DEFAULT 'SENT' COMMENT '状态：SENT=已发送, PROCESSED=已处理, IGNORED=已忽略',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='催单记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `name` varchar(50) DEFAULT NULL COMMENT '濮撳悕',
  `phone` varchar(100) NOT NULL COMMENT '鎵嬫満鍙�',
  `sex` varchar(2) DEFAULT NULL COMMENT '鎬у埆',
  `id_number` varchar(18) DEFAULT NULL COMMENT '韬唤璇佸彿',
  `avatar` varchar(500) DEFAULT NULL COMMENT '澶村儚',
  `status` int NOT NULL DEFAULT '1' COMMENT '鐘舵�� 0:绂佺敤 1:姝ｅ父',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鐢ㄦ埛淇℃伅',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛id',
  `department_id` bigint DEFAULT NULL COMMENT '部门ID（企业组织架构，2026-10-06）',
  PRIMARY KEY (`id`),
  KEY `idx_user_phone` (`phone`),
  KEY `idx_user_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `target_type` tinyint NOT NULL COMMENT '浏览对象类型 1:菜品 2:套餐',
  `target_id` bigint NOT NULL COMMENT '浏览对象ID',
  `target_name` varchar(128) DEFAULT NULL COMMENT '浏览对象名称',
  `duration_seconds` int NOT NULL DEFAULT '0' COMMENT '浏览停留时长(秒)',
  `action_type` tinyint NOT NULL DEFAULT '1' COMMENT '行为类型 1:浏览 2:收藏 3:加购 4:分享',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_time` (`user_id`,`create_time` DESC) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE,
  KEY `idx_target` (`target_type`,`target_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户浏览记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `platform` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '设备平台（ANDROID-安卓 IOS-苹果 H5-网页）',
  `device_token` varchar(255) DEFAULT NULL COMMENT '设备推送Token',
  `app_version` varchar(20) DEFAULT NULL COMMENT 'APP汾',
  `push_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否推送: 1=是 0=否',
  `last_active_time` datetime DEFAULT NULL COMMENT '最后活跃时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
  `update_user` bigint DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_platform` (`user_id`,`platform`),
  KEY `idx_device_token` (`device_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设备';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `target_type` int NOT NULL COMMENT '绫诲瀷 1鑿滃搧 2鍟嗗',
  `target_id` bigint NOT NULL COMMENT '鏀惰棌瀵硅薄ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime NOT NULL COMMENT '鏀惰棌鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_favorite` (`user_id`,`target_type`,`target_id`,`tenant_id`),
  KEY `idx_uf_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_preference_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `tag_type` tinyint NOT NULL COMMENT '标签类型 1:口味偏好 2:品类偏好 3:价格偏好 4:时段偏好',
  `tag_name` varchar(64) NOT NULL COMMENT '标签名称，如：辣味、川菜、0-30元、午市时段',
  `tag_value` decimal(5,2) NOT NULL DEFAULT '1.00' COMMENT '偏好权重 0.00~1.00，越高越偏好',
  `source` varchar(20) NOT NULL DEFAULT 'ORDER' COMMENT '数据来源 ORDER:订单分析 BROWSE:浏览分析 MANUAL:手动标注',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `create_user` bigint NOT NULL COMMENT '创建用户',
  `update_user` bigint NOT NULL COMMENT '修改人',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删除 1=已删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_tag` (`user_id`,`tag_type`) USING BTREE,
  KEY `idx_tenant` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户偏好标签';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `withdrawal_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `application_no` varchar(50) DEFAULT NULL COMMENT '鐢崇紪鍙�',
  `applicant_id` bigint DEFAULT NULL COMMENT '鐢充汉ID',
  `applicant_name` varchar(50) DEFAULT NULL COMMENT '鐢充汉',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '鎻愮幇閲�',
  `withdraw_method` int DEFAULT NULL COMMENT '鎻愮幇鏂瑰紡 1-閾� 2-鏀� 3-寰�',
  `receive_account` varchar(100) DEFAULT NULL COMMENT '鏀惰处鍙�',
  `receive_name` varchar(50) DEFAULT NULL COMMENT '鏀朵汉',
  `status` int DEFAULT '0' COMMENT '鐘� 0-寰� 1-宸� 2-宸蹭粯 3-宸叉嫆 4-宸插彇',
  `reviewer_id` bigint DEFAULT NULL COMMENT '瀹℃牳浜篒D',
  `reviewer_name` varchar(50) DEFAULT NULL COMMENT '瀹℃牳浜�',
  `review_time` datetime DEFAULT NULL COMMENT '瀹℃牳鏃堕棿',
  `review_remark` varchar(200) DEFAULT NULL COMMENT '瀹℃牳澶囨敞',
  `payment_time` datetime DEFAULT NULL COMMENT '浠樻椂闂�',
  `payment_no` varchar(50) DEFAULT NULL COMMENT '浠樼紪鍙�',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `withdrawal_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '绉熸埛ID',
  `withdrawal_id` bigint NOT NULL COMMENT '鎻愮幇鐢宠ID',
  `actual_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '瀹為檯鍒拌处閲戦',
  `fee` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鎵嬬画璐�',
  `transfer_time` datetime DEFAULT NULL COMMENT '杞处鏃堕棿',
  `bank_trace_no` varchar(100) DEFAULT '' COMMENT '閾惰娴佹按鍙�',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_withdrawal_record_tenant_id` (`tenant_id`),
  KEY `idx_withdrawal_record_withdrawal_id` (`withdrawal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `withdrawal_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '绉熸埛ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鎻愮幇閲戦',
  `bank_name` varchar(100) NOT NULL DEFAULT '' COMMENT '閾惰鍚嶇О',
  `account_name` varchar(100) NOT NULL DEFAULT '' COMMENT '寮�鎴蜂汉濮撳悕',
  `account_number` varchar(100) NOT NULL DEFAULT '' COMMENT '閾惰璐﹀彿',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '鐘舵�侊細PENDING/APPROVED/REJECTED/TRANSFERRED',
  `reject_reason` varchar(255) DEFAULT '' COMMENT '鎷掔粷鍘熷洜',
  `create_time` datetime DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `approve_time` datetime DEFAULT NULL COMMENT '瀹℃壒鏃堕棿',
  `approve_user_id` bigint DEFAULT NULL COMMENT '瀹℃壒浜篒D',
  `is_deleted` int NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎锛�0=鏈垹闄わ紝1=宸插垹闄�',
  PRIMARY KEY (`id`),
  KEY `idx_withdrawal_request_tenant_id` (`tenant_id`),
  KEY `idx_withdrawal_request_user_id` (`user_id`),
  KEY `idx_withdrawal_request_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `employee_name` varchar(50) DEFAULT NULL COMMENT '员工姓名',
  `schedule_date` date NOT NULL COMMENT '排班日期',
  `shift` tinyint DEFAULT '3' COMMENT '班次：0=早班,1=中班,2=晚班,3=全天',
  `shift_start` time DEFAULT NULL COMMENT '班次开始时间',
  `shift_end` time DEFAULT NULL COMMENT '班次结束时间',
  `work_date_str` varchar(20) DEFAULT NULL COMMENT '工作日期字符串',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_emp_date` (`employee_id`,`schedule_date`),
  KEY `idx_schedule_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

