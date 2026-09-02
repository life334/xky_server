package com.xakcch.project.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 合同数据导入-预览响应
 *
 * @author liuyonghui
 */
public class ContractImportPreviewResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会话 token（提交时回传） */
    private String token;

    /** 总行数 */
    private Integer totalRows;

    /** 可导入行数 */
    private Integer readyCount;

    /** 重复合同编号行数 */
    private Integer duplicateCount;

    /** 错误行数 */
    private Integer errorCount;

    /** 问题摘要 */
    private ProblemSummary problemSummary;

    /** 可导入行 */
    private List<ContractImportRow> rows = new ArrayList<>();

    /** 问题行（同 ContractImportRow 结构，含重复/错误行） */
    private List<ContractImportRow> problemRows = new ArrayList<>();

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getReadyCount() { return readyCount; }
    public void setReadyCount(Integer readyCount) { this.readyCount = readyCount; }
    public Integer getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(Integer duplicateCount) { this.duplicateCount = duplicateCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public ProblemSummary getProblemSummary() { return problemSummary; }
    public void setProblemSummary(ProblemSummary problemSummary) { this.problemSummary = problemSummary; }
    public List<ContractImportRow> getRows() { return rows; }
    public void setRows(List<ContractImportRow> rows) { this.rows = rows; }
    public List<ContractImportRow> getProblemRows() { return problemRows; }
    public void setProblemRows(List<ContractImportRow> problemRows) { this.problemRows = problemRows; }

    /** 问题摘要 */
    public static class ProblemSummary implements Serializable {
        private String duplicateDesc;
        private String errorDesc;
        public String getDuplicateDesc() { return duplicateDesc; }
        public void setDuplicateDesc(String v) { this.duplicateDesc = v; }
        public String getErrorDesc() { return errorDesc; }
        public void setErrorDesc(String v) { this.errorDesc = v; }
    }

    /** 合同行 */
    public static class ContractImportRow implements Serializable {
        private static final long serialVersionUID = 1L;

        /** Excel 行号（1-based） */
        private Integer excelRow;

        /** 合同编号 */
        private String contractNo;

        /** 委托单位 */
        private String clientUnit;

        /** 合同名称 */
        private String contractName;

        /** 签订时间 */
        private Date signDate;

        /** 合同金额 */
        private BigDecimal contractAmount;

        /** 合同类型（单价合同 / 总价合同） */
        private String contractType;

        /** 备注 */
        private String remark;

        /** 项目类型（自由文本，写入 extra_data） */
        private String projectType;

        /** 测绘地址（自由文本，写入 extra_data） */
        private String surveyAddress;

        /** 是否重复（合同编号已存在） */
        private boolean duplicate;

        /** 错误信息 */
        private List<String> errors = new ArrayList<>();

        public Integer getExcelRow() { return excelRow; }
        public void setExcelRow(Integer excelRow) { this.excelRow = excelRow; }
        public String getContractNo() { return contractNo; }
        public void setContractNo(String contractNo) { this.contractNo = contractNo; }
        public String getClientUnit() { return clientUnit; }
        public void setClientUnit(String clientUnit) { this.clientUnit = clientUnit; }
        public String getContractName() { return contractName; }
        public void setContractName(String contractName) { this.contractName = contractName; }
        public Date getSignDate() { return signDate; }
        public void setSignDate(Date signDate) { this.signDate = signDate; }
        public BigDecimal getContractAmount() { return contractAmount; }
        public void setContractAmount(BigDecimal contractAmount) { this.contractAmount = contractAmount; }
        public String getContractType() { return contractType; }
        public void setContractType(String contractType) { this.contractType = contractType; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getProjectType() { return projectType; }
        public void setProjectType(String projectType) { this.projectType = projectType; }
        public String getSurveyAddress() { return surveyAddress; }
        public void setSurveyAddress(String surveyAddress) { this.surveyAddress = surveyAddress; }
        public boolean isDuplicate() { return duplicate; }
        public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
}
