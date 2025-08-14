package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class VideoAnalyze {
    private VideoAssessment videoAssessment; // 视频评估结果
    private String summary;
}
