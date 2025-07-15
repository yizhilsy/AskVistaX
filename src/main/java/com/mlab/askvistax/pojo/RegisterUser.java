package com.mlab.askvistax.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegisterUser {
    @NotBlank
    @Pattern(regexp = "^\\S{3,16}$")
    private String userAccount;
    @NotBlank
    @Pattern(regexp = "^\\S{5,16}$")
    private String password;
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
    @NotNull
    private Integer gender;
}
