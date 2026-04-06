package com.apitestagent.web.dto;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class MethodSourceQueryRequest {

    @NotBlank(message = "不能为空")
    private String className;

    @NotBlank(message = "不能为空")
    private String methodName;

    private String packageName;

    private Integer parameterCount;

    private String fileHint;

    private List<String> searchRoots = new ArrayList<>();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getFileHint() {
        return fileHint;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Integer getParameterCount() {
        return parameterCount;
    }

    public void setParameterCount(Integer parameterCount) {
        this.parameterCount = parameterCount;
    }

    public void setFileHint(String fileHint) {
        this.fileHint = fileHint;
    }

    public List<String> getSearchRoots() {
        return searchRoots;
    }

    public void setSearchRoots(List<String> searchRoots) {
        this.searchRoots = searchRoots;
    }
}
