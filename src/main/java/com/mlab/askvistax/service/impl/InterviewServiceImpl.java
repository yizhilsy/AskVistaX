package com.mlab.askvistax.service.impl;

import com.mlab.askvistax.mapper.InterviewMapper;
import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.pojo.InterviewResult;
import com.mlab.askvistax.service.InterviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewServiceImpl implements InterviewService {
    @Autowired
    private InterviewMapper interviewMapper;

    @Override
    public Interview getInterviewById(Integer interviewId) {
        return interviewMapper.getInterviewById(interviewId);
    }

    @Override
    public void recordResult(InterviewResult interviewResult) {
        interviewMapper.recordResult(interviewResult);
    }

    @Override
    public List<InterviewResult> getInterviewRecordById(Integer interviewId) {
        return interviewMapper.getInterviewRecordById(interviewId);
    }

    @Override
    public void startInterview(Integer interviewId) {
        Interview interview = interviewMapper.getInterviewById(interviewId);
        interview.setStartTime(LocalDateTime.now());
        interviewMapper.updateInterview(interview);
    }

    @Override
    public void endInterview(Integer interviewId) {
        Interview interview = interviewMapper.getInterviewById(interviewId);
        interview.setEndTime(LocalDateTime.now());
        interviewMapper.updateInterview(interview);
    }

    @Override
    public void updateInterview(Interview interview) {
        interviewMapper.updateInterview(interview);
    }
}
