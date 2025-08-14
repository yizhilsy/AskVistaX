package com.mlab.askvistax.mapper;

import com.mlab.askvistax.pojo.Interview;
import com.mlab.askvistax.pojo.InterviewResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InterviewMapper {
    Interview getInterviewById(Integer interviewId);

    void recordResult(InterviewResult interviewResult);

    List<InterviewResult> getInterviewRecordById(Integer interviewId);

    void updateInterview(Interview interview);
}
