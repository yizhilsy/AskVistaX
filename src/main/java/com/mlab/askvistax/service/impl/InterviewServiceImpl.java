package com.mlab.askvistax.service.impl;

import com.mlab.askvistax.mapper.InterviewMapper;
import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.service.InterviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InterviewServiceImpl implements InterviewService {
    @Autowired
    private InterviewMapper interviewMapper;

    @Override
    public Interview getInterviewById(Integer interviewId) {
        return interviewMapper.getInterviewById(interviewId);
    }
}
