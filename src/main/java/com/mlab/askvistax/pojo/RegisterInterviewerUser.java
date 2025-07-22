package com.mlab.askvistax.pojo;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegisterInterviewerUser {
    // RegisterUser
    @Valid
    private RegisterUser registerUser;
    // RegisterInterviewer
    @Valid
    private RegisterInterviewer registerInterviewer;
}
