package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Summary {
    private String overall_assessment;
    private AbilityRadar ability_radar;
    private List<KeyIssue> key_issues;
    private List<String> behavioral_feedback;
    private String recommendation;
    private List<String> improvement_suggestions;
}
