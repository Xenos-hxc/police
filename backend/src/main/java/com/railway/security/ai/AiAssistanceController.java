package com.railway.security.ai;

import com.railway.security.ai.AiAssistanceService.Answer;
import com.railway.security.ai.AiAssistanceService.PolicyInput;
import com.railway.security.ai.AiAssistanceService.QualityResult;
import com.railway.security.ai.AiAssistanceService.RunView;
import com.railway.security.shared.web.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('BUREAU','STATION')")
@RequiredArgsConstructor
// 现有接口可封装为工具；扩展 Agent 或 MCP 时保留服务端身份、参数校验、最小权限和人工审批，禁止模型直接执行 SQL。
public class AiAssistanceController {
    private final AiAssistanceService service;

    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> status() {
        return ApiResponse.ok(Map.of("enabled", service.enabled()));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasRole('BUREAU')")
    public ApiResponse<Map<String, Long>> addPolicy(@RequestBody PolicyInput input) {
        return ApiResponse.ok(Map.of("id", service.addPolicy(input)));
    }

    @GetMapping("/policies")
    public ApiResponse<List<AiAssistanceService.PolicyView>> policies(@RequestParam long taskId) {
        return ApiResponse.ok(service.policies(taskId));
    }

    @PostMapping("/policies/{id}/reindex")
    @PreAuthorize("hasRole('BUREAU')")
    public ApiResponse<Void> reindex(@PathVariable long id) {
        service.indexPolicy(id);
        return ApiResponse.ok();
    }

    @PostMapping("/policies/{id}/retire")
    @PreAuthorize("hasRole('BUREAU')")
    public ApiResponse<Void> retire(@PathVariable long id) {
        service.retirePolicy(id);
        return ApiResponse.ok();
    }

    @PostMapping("/questions")
    public ApiResponse<Answer> question(@RequestBody QuestionRequest request) {
        return ApiResponse.ok(service.answer(request.taskId(), request.question()));
    }

    @PostMapping("/attachments/{id}/quality")
    public ApiResponse<QualityResult> quality(@PathVariable long id) {
        return ApiResponse.ok(service.quality(id));
    }

    @GetMapping("/runs")
    public ApiResponse<List<RunView>> runs(@RequestParam long taskId) {
        return ApiResponse.ok(service.runs(taskId));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<QualityResult> run(@PathVariable long id) {
        return ApiResponse.ok(service.run(id));
    }

    @PostMapping("/runs/{id}/review")
    public ApiResponse<Void> review(@PathVariable long id, @RequestBody ReviewRequest request) {
        service.review(id, request.decision());
        return ApiResponse.ok();
    }

    @PostMapping("/runs/{id}/replay")
    public ApiResponse<QualityResult> replay(@PathVariable long id) {
        return ApiResponse.ok(service.replay(id));
    }

    public record QuestionRequest(long taskId, String question) {}

    public record ReviewRequest(String decision) {}
}
