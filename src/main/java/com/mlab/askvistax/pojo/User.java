package com.mlab.askvistax.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mlab.askvistax.validation.UpdateBasicUser;
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
public class User {
    @NotNull
    private String uid;
    @NotBlank(message = "账号不能为空", groups = {UpdateBasicUser.class})
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
    @NotNull(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    @NotNull
    private Integer gender;
}
