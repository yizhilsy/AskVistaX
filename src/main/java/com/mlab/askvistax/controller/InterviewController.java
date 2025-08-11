package com.mlab.askvistax.controller;

import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.pojo.Interviewer;
import com.mlab.askvistax.pojo.Result;
import com.mlab.askvistax.service.InterviewService;
import com.mlab.askvistax.utils.ResumeGenerateQuestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interview")
@Validated
@Slf4j
public class InterviewController {
    @Autowired
    private InterviewService interviewService;



}
