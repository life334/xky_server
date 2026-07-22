package com.xakcch.project.mapper;

import java.util.List;
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
}
