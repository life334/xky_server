package com.xakcch.project.service;

import java.util.List;
import com.xakcch.project.domain.ProjMaterial;

/**
 * 资料提交 业务层接口
 *
 * @author liuyonghui
 */
public interface IProjMaterialService
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
     * 新增资料提交
     */
    public int insertMaterial(ProjMaterial material);

    /**
     * 修改资料提交
     */
    public int updateMaterial(ProjMaterial material);

    /**
     * 批量删除
     */
    public int deleteMaterialByIds(Long[] ids);
}
