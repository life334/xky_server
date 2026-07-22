package com.xakcch.project.mapper;

import java.util.List;
import java.util.Map;
import com.xakcch.project.domain.ProjProject;

/**
 * 项目主表 数据层
 *
 * @author liuyonghui
 */
public interface ProjProjectMapper
{
    /**
     * 查询项目详情（含关联的类别名、合同名、负责人名）
     *
     * @param id 项目ID
     * @return 项目
     */
    public ProjProject selectProjectById(Long id);

    /**
     * 查询项目列表（分页，含关联信息）
     *
     * @param project 查询条件
     * @return 项目集合
     */
    public List<ProjProject> selectProjectList(ProjProject project);

    /**
     * 校验工程编号是否唯一
     *
     * @param project 项目信息
     * @return 项目
     */
    public ProjProject checkProjectCodeUnique(ProjProject project);

    /**
     * 新增项目
     *
     * @param project 项目
     * @return 结果
     */
    public int insertProject(ProjProject project);

    /**
     * 修改项目
     *
     * @param project 项目
     * @return 结果
     */
    public int updateProject(ProjProject project);

    /**
     * 删除项目（逻辑删除）
     *
     * @param id 项目ID
     * @return 结果
     */
    public int deleteProjectById(Long id);

    /**
     * 批量删除项目（逻辑删除）
     *
     * @param ids 需要删除的项目ID
     * @return 结果
     */
    public int deleteProjectByIds(Long[] ids);

    /**
     * 办结项目（状态改为已办结）
     *
     * @param id 项目ID
     * @return 结果
     */
    public int completeProject(Long id);

    /**
     * 按合同ID查询关联项目（含类别名+合同单价，用于合同结算树形展示）
     *
     * @param contractId 合同ID
     * @return 项目列表（Map: project_id, project_code, project_name, engineering_project, client_unit, category_name, contract_price）
     */
    public List<Map<String, Object>> selectProjectsByContractId(Long contractId);
}
