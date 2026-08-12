package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
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
}
