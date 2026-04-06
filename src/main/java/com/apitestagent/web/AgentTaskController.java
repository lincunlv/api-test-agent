package com.apitestagent.web;

import com.apitestagent.service.AgentTaskService;
import com.apitestagent.web.dto.CreateTaskRequest;
import com.apitestagent.web.dto.TaskView;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.io.IOException;

@Validated
@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

    private final AgentTaskService agentTaskService;

    public AgentTaskController(AgentTaskService agentTaskService) {
        this.agentTaskService = agentTaskService;
    }

    @PostMapping
    public TaskView createTask(@Valid @RequestBody CreateTaskRequest request) throws IOException {
        return agentTaskService.createTask(request);
    }

    @GetMapping("/{taskId}")
    public TaskView getTask(@PathVariable("taskId") String taskId) {
        return agentTaskService.getTask(taskId);
    }
}
