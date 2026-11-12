package com.xakcch.web.controller.project;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xakcch.common.core.controller.BaseController;
import com.xakcch.common.core.domain.AjaxResult;
import com.xakcch.project.report.ProjectExportFieldPool;

/**
 * 项目导出字段池（两页共用）。
 *
 * <p>项目列表页与费用结算页的「导出」弹窗都从这里取可选字段清单，
 * 保证「不管在哪个页面导出，能选的字段完全一样」（2026-11-11 需求）。
 *
 * <p>⚠️ 只是列元数据（key/label/类型/分组/默认勾选），不含业务数据；
 *    登录即可读，与「参考数据」只读接口同策略，不挂具体业务权限码
 *    （挂权限码曾导致无该菜单权限的用户 403，见 MEMORY 铁律 #15）。
 */
@RestController
@RequestMapping("/project/export")
public class ProjectExportFieldController extends BaseController
{
    /**
     * 字段池
     *
     * @param page {@code project}（项目列表，默认）/ {@code settlement}（费用结算）
     *             —— 仅影响 defaultVisible（两页默认勾选列不同），字段清单本身完全一致
     */
    @GetMapping("/fields")
    public AjaxResult fields(@RequestParam(value = "page", required = false) String page)
    {
        return success(ProjectExportFieldPool.toMeta(page));
    }
}
