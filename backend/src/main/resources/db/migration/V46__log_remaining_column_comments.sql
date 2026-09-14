-- 补齐日志域剩余表的字段中文注释（节气卡片任务、站内消息、媒体资产与集成出站事件）。
-- 仍以 MODIFY COLUMN 复刻完整定义后追加 COMMENT，不改变列类型、可空性、默认值与索引。

-- ===== 节气卡片任务 freshmart_log.holiday_card_tasks =====
ALTER TABLE freshmart_log.holiday_card_tasks
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN task_key VARCHAR(64) NOT NULL COMMENT '任务标识，全局唯一',
  MODIFY COLUMN holiday_key VARCHAR(32) NOT NULL COMMENT '节气或节日标识',
  MODIFY COLUMN greeting VARCHAR(120) NOT NULL COMMENT '卡片祝福语',
  MODIFY COLUMN scheduled_at DATETIME NOT NULL COMMENT '计划发送时间',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：待执行、执行中、已完成、执行失败',
  MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '创建人',
  MODIFY COLUMN started_at DATETIME NULL COMMENT '开始执行时间',
  MODIFY COLUMN completed_at DATETIME NULL COMMENT '执行完成时间',
  MODIFY COLUMN sent_count INT NOT NULL DEFAULT 0 COMMENT '投递成功人数',
  MODIFY COLUMN failed_count INT NOT NULL DEFAULT 0 COMMENT '投递失败人数，失败会在下一轮重试',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 站内消息 freshmart_log.inbox_messages =====
ALTER TABLE freshmart_log.inbox_messages
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '接收用户',
  MODIFY COLUMN message_type VARCHAR(32) NOT NULL COMMENT '消息类型：支付、退款、节气卡片等',
  MODIFY COLUMN title VARCHAR(120) NOT NULL COMMENT '标题',
  MODIFY COLUMN body VARCHAR(1000) NOT NULL COMMENT '正文',
  MODIFY COLUMN business_type VARCHAR(32) NULL COMMENT '关联业务类型',
  MODIFY COLUMN business_id BIGINT NULL COMMENT '关联业务主键',
  MODIFY COLUMN read_at DATETIME NULL COMMENT '阅读时间，为空表示未读',
  MODIFY COLUMN card_svg_content LONGTEXT NULL COMMENT '卡片 SVG 内容，已转义可直接内联展示',
  MODIFY COLUMN card_svg_url VARCHAR(512) NULL COMMENT '卡片资源地址',
  MODIFY COLUMN idempotency_key VARCHAR(100) NULL COMMENT '幂等键，节气卡片按任务与用户去重',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- ===== 媒体资产 freshmart_log.media_assets =====
ALTER TABLE freshmart_log.media_assets
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN storage_key VARCHAR(64) NOT NULL COMMENT '存储名，随机生成以避免覆盖',
  MODIFY COLUMN storage_file_name VARCHAR(100) NOT NULL COMMENT '实际落盘文件名',
  MODIFY COLUMN uploader_user_id BIGINT NOT NULL COMMENT '上传人，决定谁有权读取',
  MODIFY COLUMN original_file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  MODIFY COLUMN content_type VARCHAR(80) NOT NULL COMMENT '内容类型，限 JPEG、PNG、WebP、MP4、MOV',
  MODIFY COLUMN size_bytes BIGINT NOT NULL COMMENT '文件大小（字节），单文件不超过 30 MB',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间';

-- ===== 集成出站事件 freshmart_log.integration_outbox =====
ALTER TABLE freshmart_log.integration_outbox
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  MODIFY COLUMN event_type VARCHAR(80) NOT NULL COMMENT '事件类型',
  MODIFY COLUMN aggregate_type VARCHAR(48) NOT NULL COMMENT '聚合根类型',
  MODIFY COLUMN aggregate_id VARCHAR(80) NOT NULL COMMENT '聚合根标识',
  MODIFY COLUMN payload_json JSON NOT NULL COMMENT '事件负载',
  MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：待投递、已投递、失败',
  MODIFY COLUMN available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '可投递时间，用于退避重试',
  MODIFY COLUMN processed_at DATETIME NULL COMMENT '投递完成时间',
  MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
