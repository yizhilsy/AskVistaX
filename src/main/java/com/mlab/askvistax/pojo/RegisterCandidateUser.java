package com.mlab.askvistax.pojo;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Data
public class RegisterCandidateUser {
    // RegisterUser
    @Valid
    private RegisterUser registerUser;
    // RegisterCandidate
    @Valid
    private RegisterCandidate registerCandidate;
}
