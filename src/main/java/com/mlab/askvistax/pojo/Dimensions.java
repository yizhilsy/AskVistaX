package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Dimensions {
    private double completeness;
    private double accuracy;
    private double clarity;
    private double depth;
    private double experience;
}
