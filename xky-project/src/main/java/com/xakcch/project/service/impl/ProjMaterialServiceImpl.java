package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.xakcch.project.domain.ProjMaterial;
import com.xakcch.project.mapper.ProjMaterialMapper;
import com.xakcch.project.service.IProjMaterialService;

/**
 * 资料提交 业务层实现
 *
 * @author liuyonghui
 */
@Service
public class ProjMaterialServiceImpl implements IProjMaterialService
{
    @Autowired
    private ProjMaterialMapper materialMapper;

    @Override
    public List<ProjMaterial> selectMaterialList(ProjMaterial material)
    {
        return materialMapper.selectMaterialList(material);
    }

    @Override
    public ProjMaterial selectMaterialById(Long id)
    {
        return materialMapper.selectMaterialById(id);
    }

    @Override
    public int insertMaterial(ProjMaterial material)
    {
        return materialMapper.insertMaterial(material);
    }

    @Override
    public int updateMaterial(ProjMaterial material)
    {
        return materialMapper.updateMaterial(material);
    }

    @Override
    public int deleteMaterialByIds(Long[] ids)
    {
        return materialMapper.deleteMaterialByIds(ids);
    }
}
