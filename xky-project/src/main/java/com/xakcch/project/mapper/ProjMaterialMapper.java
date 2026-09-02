package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.xakcch.project.domain.ProjMaterial;

/**
 * 资料提交 数据层
 *
 * @author liuyonghui
 */
public interface ProjMaterialMapper
{
    /**
     * 查询资料提交列表
     */
    public List<ProjMaterial> selectMaterialList(ProjMaterial material);

    /**
     * 根据ID查询
     */
    public ProjMaterial selectMaterialById(Long id);

    /**
     * 新增
     */
    public int insertMaterial(ProjMaterial material);

    /**
     * 修改
     */
    public int updateMaterial(ProjMaterial material);

    /**
     * 批量删除（逻辑删除）
     */
    public int deleteMaterialByIds(Long[] ids);

    /**
     * 逻辑删除某项目的全部资料（删除项目时级联清理）
     *
     * @param projectId 项目ID
     * @return 结果
     */
    public int deleteMaterialsByProjectId(Long projectId);

    /**
     * 逻辑删除指定项目ID数组下的全部资料（批量删除项目时级联清理）
     *
     * @param projectIds 项目ID数组
     * @return 结果
     */
    public int deleteMaterialsByProjectIds(@Param("projectIds") Long[] projectIds);

    /**
     * 更新资料状态
     */
    public int updateMaterialStatus(@Param("id") Long id, @Param("status") String status, @Param("updateBy") String updateBy);

    /**
     * 按状态统计资料数量（状态胶囊导航用）
     *
     * @return [{ status: "pending", cnt: 5 }, ...]
     */
    public List<Map<String, Object>> selectMaterialStatusCounts();

    /**
     * 查询 proj_material 表的物理列元数据（information_schema 动态读取，列显隐功能用）
     *
     * @param tableName 表名（proj_material）
     * @return [{ columnName: "submit_time", columnComment: "提交时间" }, ...]
     */
    public List<Map<String, Object>> selectTableColumns(@Param("tableName") String tableName);

    /**
     * 快捷切换归档状态
     */
    public int updateArchiveFlag(@Param("id") Long id, @Param("archiveFlag") String archiveFlag, @Param("updateBy") String updateBy);

    /**
     * 查询项目欠款信息（合同金额 - 已收金额）
     */
    public Map<String, Object> selectPaymentInfo(@Param("projectId") Long projectId);
}
