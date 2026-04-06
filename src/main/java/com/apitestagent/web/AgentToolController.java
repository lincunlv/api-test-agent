package com.apitestagent.web;

import java.io.IOException;

import javax.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apitestagent.service.GitDiffService;
import com.apitestagent.service.MethodSourceService;
import com.apitestagent.web.dto.GitDiffQueryRequest;
import com.apitestagent.web.dto.GitDiffView;
import com.apitestagent.web.dto.GitHistoryQueryRequest;
import com.apitestagent.web.dto.GitHistoryView;
import com.apitestagent.web.dto.MethodSourceQueryRequest;
import com.apitestagent.web.dto.MethodSourceView;

@Validated
@RestController
@RequestMapping("/api/agent/tools")
public class AgentToolController {

    private final GitDiffService gitDiffService;

    private final MethodSourceService methodSourceService;

    public AgentToolController(GitDiffService gitDiffService, MethodSourceService methodSourceService) {
        this.gitDiffService = gitDiffService;
        this.methodSourceService = methodSourceService;
    }

    @PostMapping("/get-git-diff")
    public GitDiffView getGitDiff(@RequestBody(required = false) GitDiffQueryRequest request) throws IOException {
        return gitDiffService.getGitDiff(request);
    }

    @PostMapping("/get-git-history")
    public GitHistoryView getGitHistory(@RequestBody(required = false) GitHistoryQueryRequest request) throws IOException {
        return gitDiffService.getGitHistory(request);
    }

    @PostMapping("/get-method-source")
    public MethodSourceView getMethodSource(@Valid @RequestBody MethodSourceQueryRequest request) throws IOException {
        return methodSourceService.findMethodSource(request);
    }
}
