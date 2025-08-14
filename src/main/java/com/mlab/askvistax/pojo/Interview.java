package com.mlab.askvistax.pojo;

import com.mlab.askvistax.service.InterviewService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Interview {
    private Integer interviewId;
    private String uid;
    private Integer postId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String videoUrl;
    private String resumeUrl;
    private VideoAnalyze videoAnalyzeResult; // 视频分析结果
    private AudioAnalyze audioAnalyzeResult; // 音频分析结果
}
