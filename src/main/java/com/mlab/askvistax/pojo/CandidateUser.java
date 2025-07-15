package com.mlab.askvistax.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CandidateUser {
    // users表
    @NotNull
    private String uid;
    @NotBlank
    private String userAccount;
    @NotNull
    private String userName;
    @NotNull
    private Integer roleType;
    @NotEmpty
    private String avatar;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birth;
    @NotNull
    private String phone;
    @Email(message = "邮箱格式不正确")
    @NotNull(message = "邮箱不能为空")
    private String email;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    @NotNull
    private Integer gender;

    // candidates表
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
}
