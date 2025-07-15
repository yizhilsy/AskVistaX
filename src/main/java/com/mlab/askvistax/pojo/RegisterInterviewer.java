package com.mlab.askvistax.pojo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegisterInterviewer {
    @NotNull
    private String realName;
    @NotNull
    private String businessGroup;
    @NotNull
    private String department;
    @NotNull
    private String rankLevel;
    @NotNull
    private String position;
    private String userAccount;
}
