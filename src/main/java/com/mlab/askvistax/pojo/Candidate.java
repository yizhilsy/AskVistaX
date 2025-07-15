package com.mlab.askvistax.pojo;

import com.mlab.askvistax.validation.UpdateCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Candidate {
    @NotNull
    private Integer candId;
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
    @NotBlank(message = "关联账号不能为空", groups = {UpdateCandidate.class})
    private String userAccount;
}
