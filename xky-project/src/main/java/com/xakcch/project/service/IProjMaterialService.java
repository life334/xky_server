package com.xakcch.project.service;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.domain.ProjMaterialFlow;

/**
 * 资料提交 业务层接口
 *
 * @author liuyonghui
 */
public interface IProjMaterialService
{
    public List<ProjMaterial> selectMaterialList(ProjMaterial material);
    public ProjMaterial selectMaterialById(Long id);
    public int insertMaterial(ProjMaterial material);
    public int updateMaterial(ProjMaterial material);
    public int deleteMaterialByIds(Long[] ids);

    /**
     * 领取资料
     * @param materialId 资料ID
     * @param guarantorId 担保人ID
     * @param remark 备注
     * @param userId 操作人ID
     * @param userName 操作人姓名
     */
    public void borrowMaterial(Long materialId, Long guarantorId, String remark, Long userId, String userName);

    /**
     * 归还资料
     */
    public void returnMaterial(Long materialId, String remark, Long userId, String userName);

    /**
     * 查询流转记录
     */
    public List<ProjMaterialFlow> getFlowList(Long materialId);

    /**
     * 统计各状��下的资料数量
     *
     * @return [{ status: "pending", cnt: 5 }, ...]
     */
    public List<Map<String, Object>> getStatusCounts();
}
