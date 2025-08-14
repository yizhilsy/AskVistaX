package com.mlab.askvistax.service;

import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.pojo.InterviewResult;

import java.util.List;

public interface InterviewService {
    Interview getInterviewById(Integer interviewId);

    void recordResult(InterviewResult interviewResult);

    List<InterviewResult> getInterviewRecordById(Integer interviewId);

    void startInterview(Integer interviewId);

    void endInterview(Integer interviewId);

    void updateInterview(Interview interview);
}
