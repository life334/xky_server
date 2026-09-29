-- ============================================================================
-- 18_add_related_project_code.sql
-- ============================================================================

alter table proj_project add column if not exists related_project_code_text varchar(50);

comment on column proj_project.related_project_code_text is
  '关联定线编号（手输文本：系统中不存在该定线项目时使用；有关联项目时以 related_project_id 为准）';
