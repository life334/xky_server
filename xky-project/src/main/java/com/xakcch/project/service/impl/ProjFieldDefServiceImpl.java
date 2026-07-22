package com.xakcch.project.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xakcch.project.domain.ProjFieldDef;
import com.xakcch.project.mapper.ProjFieldDefMapper;
import com.xakcch.project.service.IProjFieldDefService;

/**
 * 动态字段定义 Service业务层处理
 *
 * @author liuyonghui
 */
@Service
public class ProjFieldDefServiceImpl implements IProjFieldDefService
{
    @Autowired
    private ProjFieldDefMapper fieldDefMapper;

    @Override
    public List<ProjFieldDef> selectFieldDefList(ProjFieldDef fieldDef)
    {
        return fieldDefMapper.selectFieldDefList(fieldDef);
    }

    @Override
    public ProjFieldDef selectFieldDefById(Long id)
    {
        return fieldDefMapper.selectFieldDefById(id);
    }

    @Override
    public int insertFieldDef(ProjFieldDef fieldDef)
    {
        return fieldDefMapper.insertFieldDef(fieldDef);
    }

    @Override
    public int updateFieldDef(ProjFieldDef fieldDef)
    {
        return fieldDefMapper.updateFieldDef(fieldDef);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteFieldDefByIds(Long[] ids)
    {
        return fieldDefMapper.deleteFieldDefByIds(ids);
    }

    @Override
    public List<ProjFieldDef> selectFieldDefByTableName(String tableName)
    {
        return fieldDefMapper.selectFieldDefByTableName(tableName);
    }
}
