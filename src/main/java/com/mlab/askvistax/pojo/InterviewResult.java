package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class InterviewResult {
    private Integer interviewResultId; // 面试结果编号
    private Integer interviewId;       // 面试过程编号
    private String question;           // 面试问题
    private String answer;              // 问题回答
    private Double overallScore;        // 综合评分
    private Dimensions dimensions;      // 评分各维度及分数（JSON）
    private String feedback;            // 回答反馈
    private Boolean needFollowup;       // 是否需要追问
}
