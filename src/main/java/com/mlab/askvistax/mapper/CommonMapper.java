package com.mlab.askvistax.mapper;

import com.mlab.askvistax.pojo.*;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommonMapper {
    User getUserByUserAccount(String userAccount);
    String getPasswordHashByUserAccount(String userAccount);
    CandidateUser getCandidateUserByUserAccount(String userAccount);
    InterviewerUser getInterviewerUserByUserAccount(String userAccount);
    void addUser(User user, String passwordHash);
    void addCandidate(RegisterCandidate regCandidate);
    void addInterviewer(RegisterInterviewer registerInterviewer);
    void updateBasicUser(User user);
    void updateCandidate(Candidate candidate);
    void updateInterviewer(Interviewer interviewer);
    void updateUserPwd(String userAccount, String newPasswordHash);
}
