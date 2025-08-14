package com.mlab.askvistax.controller;

import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.pojo.InterviewResult;
import com.mlab.askvistax.pojo.Interviewer;
import com.mlab.askvistax.pojo.Result;
import com.mlab.askvistax.service.InterviewService;
import com.mlab.askvistax.utils.ResumeGenerateQuestion;
import com.mlab.askvistax.utils.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interview")
@Validated
@Slf4j
public class InterviewController {
    @Autowired
    private InterviewService interviewService;

    @GetMapping("/getInterviewById")
    public Result getInterviewById(@RequestParam Integer interviewId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");

        Interview interview = interviewService.getInterviewById(interviewId);
        log.info("账号名为: {}的用户: {}查询面试过程成功, roleType: {}, interviewId: {}", userAccount, userName, roleType, interviewId);
        return Result.success(interview);
    }

    @GetMapping("/getInterviewRecordById")
    public Result getInterviewRecordById(@RequestParam Integer interviewId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");

        List<InterviewResult> interviewResults = interviewService.getInterviewRecordById(interviewId);
        log.info("账号名为: {}的用户: {}查询面试记录成功, roleType: {}, interviewId: {}", userAccount, userName, roleType, interviewId);
        return Result.success(interviewResults);
    }

    @PatchMapping("startInterview")
    public Result startInterview(@RequestParam Integer interviewId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");

        interviewService.startInterview(interviewId);
        log.info("账号名为: {}的用户: {}开始面试成功, roleType: {}, interviewId: {}", userAccount, userName, roleType, interviewId);
        return Result.success();
    }

    @PatchMapping("endInterview")
    public Result endInterview(@RequestParam Integer interviewId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");

        interviewService.endInterview(interviewId);
        log.info("账号名为: {}的用户: {}终止面试成功, roleType: {}, interviewId: {}", userAccount, userName, roleType, interviewId);
        return Result.success();
    }


}
