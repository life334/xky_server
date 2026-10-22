package com.xakcch.project.domain.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据库恢复任务状态（供页面轮询）
 *
 * <p>恢复是异步执行的：pg_restore 处理大库可能要几分钟，不能让 HTTP 请求一直挂着
 * （网关/浏览器都会超时）。后端起线程跑，前端轮询这个对象展示阶段 + 实时日志。</p>
 *
 * @author liuyonghui
 */
public class DbRestoreStatus implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否有恢复任务正在执行 */
    private boolean running;

    /** 阶段描述：准备中 / 创建目标库 / 恢复数据中 / 校验结果 */
    private String phase;

    /** true=成功，false=失败，null=未完成（页面据此区分「进行中/成功/失败」） */
    private Boolean success;

    /** 结果或失败原因 */
    private String message;

    /** 备份文件名 */
    private String file;

    /** 目标库名 */
    private String targetDb;

    /** 目标库是否为本次新建（新建的库失败后可直接删掉重来） */
    private boolean createdDb;

    /** 恢复完成后目标库的表数量（-1 表示未统计） */
    private int tableCount = -1;

    /** 开始时间 */
    private Date startedAt;

    /** 结束时间 */
    private Date finishedAt;

    /** 耗时（毫秒） */
    private long costMs;

    /** pg_restore 输出日志（仅保留最后若干行，避免无限增长） */
    private String log;

    public DbRestoreStatus()
    {
    }

    public boolean isRunning()
    {
        return running;
    }

    public void setRunning(boolean running)
    {
        this.running = running;
    }

    public String getPhase()
    {
        return phase;
    }

    public void setPhase(String phase)
    {
        this.phase = phase;
    }

    public Boolean getSuccess()
    {
        return success;
    }

    public void setSuccess(Boolean success)
    {
        this.success = success;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getFile()
    {
        return file;
    }

    public void setFile(String file)
    {
        this.file = file;
    }

    public String getTargetDb()
    {
        return targetDb;
    }

    public void setTargetDb(String targetDb)
    {
        this.targetDb = targetDb;
    }

    public boolean isCreatedDb()
    {
        return createdDb;
    }

    public void setCreatedDb(boolean createdDb)
    {
        this.createdDb = createdDb;
    }

    public int getTableCount()
    {
        return tableCount;
    }

    public void setTableCount(int tableCount)
    {
        this.tableCount = tableCount;
    }

    public Date getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(Date startedAt)
    {
        this.startedAt = startedAt;
    }

    public Date getFinishedAt()
    {
        return finishedAt;
    }

    public void setFinishedAt(Date finishedAt)
    {
        this.finishedAt = finishedAt;
    }

    public long getCostMs()
    {
        return costMs;
    }

    public void setCostMs(long costMs)
    {
        this.costMs = costMs;
    }

    public String getLog()
    {
        return log;
    }

    public void setLog(String log)
    {
        this.log = log;
    }
}
