package com.mlab.askvistax.service.impl;

import com.mlab.askvistax.mapper.CommonMapper;
import com.mlab.askvistax.pojo.*;
import com.mlab.askvistax.service.CommonService;
import com.mlab.askvistax.utils.Md5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CommonServiceImpl implements CommonService {
    @Autowired
    private CommonMapper commonMapper;

    @Override
    public User getUserByUserAccount(String userAccount) {
        return commonMapper.getUserByUserAccount(userAccount);
    }

    @Override
    public String getPasswordHashByUserAccount(String userAccount) {
        return commonMapper.getPasswordHashByUserAccount(userAccount);
    }

    @Override
    public CandidateUser getCandidateUserByUserAccount(String userAccount) {
        return commonMapper.getCandidateUserByUserAccount(userAccount);
    }

    @Override
    public InterviewerUser getInterviewerUserByUserAccount(String userAccount) {
        return commonMapper.getInterviewerUserByUserAccount(userAccount);
    }

    @Override
    public void addUser(RegisterUser regUser) {
        // 生成用户密码的MD5哈希值
        String passwordHash = Md5Util.getMD5String(regUser.getPassword());
        // 生成一个新的UUID作为用户唯一标识符
        UUID newuuid = UUID.randomUUID();

        User newUser = new User();
        newUser.setUid(newuuid.toString());
        newUser.setUserAccount(regUser.getUserAccount());
        newUser.setUserName(regUser.getUserName());
        newUser.setRoleType(regUser.getRoleType());
        newUser.setAvatar(regUser.getAvatar());
        newUser.setBirth(regUser.getBirth());
        newUser.setPhone(regUser.getPhone());
        newUser.setEmail(regUser.getEmail());
        newUser.setCreateTime(LocalDateTime.now());
        newUser.setUpdateTime(LocalDateTime.now());
        newUser.setGender(regUser.getGender());

        commonMapper.addUser(newUser, passwordHash);
    }

    @Override
    public void addCandidate(RegisterCandidate regCandidate) {
        commonMapper.addCandidate(regCandidate);
    }

    @Override
    public void addInterviewer(RegisterInterviewer registerInterviewer) {
        commonMapper.addInterviewer(registerInterviewer);
    }

    @Override
    public void updateBasicUser(User user) {
        user.setUpdateTime(LocalDateTime.now());
        commonMapper.updateBasicUser(user);
    }

    @Override
    public void updateCandidate(Candidate candidate) {
        commonMapper.updateCandidate(candidate);
    }

    @Override
    public void updateInterviewer(Interviewer interviewer) {
        commonMapper.updateInterviewer(interviewer);
    }

    @Override
    public void updateUserPwd(String userAccount, String newPwd) {
        // 将新密码进行MD5加密
        String newPasswordHash = Md5Util.getMD5String(newPwd);
        commonMapper.updateUserPwd(userAccount, newPasswordHash);
    }
}
