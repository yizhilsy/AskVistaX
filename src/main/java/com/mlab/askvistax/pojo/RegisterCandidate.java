package com.mlab.askvistax.pojo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegisterCandidate {
    @NotNull
    private String realName;
    @NotNull
    private Integer education;
    @NotNull
    private String university;
    @NotNull
    private String major;
    @NotNull
    private Integer applyType;
    private String userAccount;
}
