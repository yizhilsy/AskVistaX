package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoAssessment {
    private double engagement;
    private double confidence;
    private double stress;
    private String notes;
}
