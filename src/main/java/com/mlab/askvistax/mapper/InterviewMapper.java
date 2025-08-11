package com.mlab.askvistax.mapper;

import com.mlab.askvistax.pojo.Interview;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewMapper {
    Interview getInterviewById(Integer interviewId);
}
