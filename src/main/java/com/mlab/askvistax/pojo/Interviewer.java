package com.mlab.askvistax.pojo;

import com.mlab.askvistax.validation.UpdateCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Interviewer {
    @NotNull
    private Integer interId;
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
    @NotBlank(message = "关联账号不能为空", groups = {UpdateCandidate.class})
    private String userAccount;
}
