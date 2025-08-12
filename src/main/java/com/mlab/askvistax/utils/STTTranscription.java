package com.mlab.askvistax.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class STTTranscription {
    private String text;
    private boolean isFinal;
    private Long bg;     // start ms（若能解析）
    private Long ed;     // end ms（若能解析）
}
