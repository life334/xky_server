package com.xakcch.project.domain.vo;

import java.io.Serializable;
import java.util.List;

public class ImportCommitRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String token;
    private List<ImportPreviewRow> rows;

    public String getToken() { return token; }
    public void setToken(String t) { this.token = t; }
    public List<ImportPreviewRow> getRows() { return rows; }
    public void setRows(List<ImportPreviewRow> r) { this.rows = r; }
}
