package com.mlab.askvistax.service;

import com.mlab.askvistax.pojo.*;

public interface CommonService {
    User getUserByUserAccount(String userAccount);
    String getPasswordHashByUserAccount(String userAccount);
    CandidateUser getCandidateUserByUserAccount(String userAccount);
    InterviewerUser getInterviewerUserByUserAccount(String userAccount);
    void addUser(RegisterUser regUser);
    void addCandidate(RegisterCandidate regCandidate);
    void addInterviewer(RegisterInterviewer registerInterviewer);
    void updateBasicUser(User user);
    void updateCandidate(Candidate candidate);
    void updateInterviewer(Interviewer interviewer);
    void updateUserPwd(String userAccount, String newPwd);
}
