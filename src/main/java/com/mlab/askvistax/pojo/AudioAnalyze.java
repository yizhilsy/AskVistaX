package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AudioAnalyze {
    private String tone;
    private String pitchVariation;
    private String speechRate;
    private String summary;
}
