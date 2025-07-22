package com.mlab.askvistax.controller;

import com.mlab.askvistax.pojo.*;
import com.mlab.askvistax.service.CommonService;
import com.mlab.askvistax.utils.*;
import com.mlab.askvistax.validation.UpdateBasicUser;
import com.mlab.askvistax.validation.UpdateCandidate;
import com.mlab.askvistax.validation.UpdateInterviewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/common")
@Validated
@Slf4j
public class CommonController {
    @Autowired
    private CommonService commonService;

    @Autowired
    private HuaWeiOBSUtils huaWeiOBSUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private GenerateToken generateToken;

    // 应聘者注册接口
    @PostMapping("/registerCandidate")
    public Result registerCandidate(@RequestBody @Validated RegisterCandidateUser registerCandidateUser) {
        User existingUser = commonService.getUserByUserAccount(registerCandidateUser.getRegisterUser().getUserAccount());
        if (existingUser != null) {
            log.info("账号名为: {}的账号已经被注册，注册失败!", registerCandidateUser.getRegisterUser().getUserAccount());
            return Result.error("该账号已被注册！");
        }
        else if (registerCandidateUser.getRegisterUser().getRoleType() != 2) {
            log.info("账号名为: {}的注册失败！注册时未合法指定应聘者角色类型！", registerCandidateUser.getRegisterUser().getUserAccount());
            return Result.error("注册失败！请指定应聘者角色类型！");
        }
        else {
            RegisterUser registerUser = registerCandidateUser.getRegisterUser();
            RegisterCandidate registerCandidate = registerCandidateUser.getRegisterCandidate();
            // 注册账号主体信息
            commonService.addUser(registerUser);
            // 注册账号身份信息：应聘者表
            registerCandidate.setUserAccount(registerUser.getUserAccount());
            commonService.addCandidate(registerCandidate);
            log.info("账号名为: {}的应聘者用户注册成功!", registerUser.getUserAccount());
            return Result.success();
        }
    }

    // 面试官注册接口
    @PostMapping("/registerInterviewer")
    public Result registerInterviewer(@RequestBody @Validated RegisterInterviewerUser registerInterviewerUser) {
        User existingUser = commonService.getUserByUserAccount(registerInterviewerUser.getRegisterUser().getUserAccount());
        if (existingUser != null) {
            log.info("账号名为: {}的账号已经被注册，注册失败!", registerInterviewerUser.getRegisterUser().getUserAccount());
            return Result.error("该账号已被注册！");
        }
        else if (registerInterviewerUser.getRegisterUser().getRoleType() != 0 && registerInterviewerUser.getRegisterUser().getRoleType() != 1) {
            log.info("账号名为: {}的注册失败！注册时未合法指定公司内部角色类型！", registerInterviewerUser.getRegisterUser().getUserAccount());
            return Result.error("注册失败！请指定公司内部角色类型！");
        }
        else {
            RegisterUser registerUser = registerInterviewerUser.getRegisterUser();
            RegisterInterviewer registerInterviewer = registerInterviewerUser.getRegisterInterviewer();
            // 注册账号主体信息
            commonService.addUser(registerUser);
            // 注册账号身份信息：应聘者表
            registerInterviewer.setUserAccount(registerUser.getUserAccount());
            commonService.addInterviewer(registerInterviewer);
            log.info("账号名为: {}的公司内部角色类型用户注册成功!", registerUser.getUserAccount());
            return Result.success();
        }
    }

    /**
     * 登录接口
     * @param userAccount
     * @param password
     * @return Result.success(token) / Result.error("账号不存在！") / Result.error("账号密码错误！")
     */
    @PostMapping("/login")
    public Result login(@Pattern(regexp = "^\\S{3,16}$") String userAccount, @Pattern(regexp = "^\\S{5,16}$") String password) {
        User user = commonService.getUserByUserAccount(userAccount);
        if (user == null) {
            log.info("账号名为: {}的账号不存在，登录失败!", userAccount);
            return Result.error("账号不存在！");
        }

        String passwordHash = commonService.getPasswordHashByUserAccount(userAccount);
        if (Md5Util.getMD5String(password).equals(passwordHash)) {  //密码与数据库中存储的密码匹配
            // 清理redis缓存中存储的该账号旧token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String oldToken = operations.get(userAccount);
            if (oldToken != null) {
                stringRedisTemplate.delete(userAccount);
                stringRedisTemplate.delete(oldToken);
            }
            // 构造业务数据并颁发新令牌
            String token = generateToken.generate(user.getUid(), user.getUserAccount(), user.getUserName(), user.getRoleType());
            // 把新token存储到redis中
            operations.set(token, token, 1, TimeUnit.HOURS);
            operations.set(userAccount, token, 1, TimeUnit.HOURS);
            log.info("账号名为: {}的用户: {}成功登录！roleType: {}", userAccount, user.getUserName(), CommonConstants.roleTypeMap.get(user.getRoleType()));
            return Result.success(token);
        }
        else {  // 密码与数据库中存储的密码不匹配
            return Result.error("账号密码错误！");
        }
    }

    /**
     * 登出销毁token接口
     * @return Result.success()
     */
    @PostMapping("/logout")
    public Result logout() {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");
        String token = httpServletRequest.getHeader("Authorization");
        // 从redis缓存中删除token
        stringRedisTemplate.delete(token);
        stringRedisTemplate.delete(userAccount);
        log.info("账号名为: {}的用户: {}成功登出！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
         // 清理ThreadLocal中的数据
        return Result.success();
    }

    /**
     * 查询个人信息接口
     * @return Result.success(CandidateUser) / Result.success(InterviewerUser)
     */
    @GetMapping("/getMyInfo")
    public Result getMyInfo() {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        Integer roleType = (Integer) map.get("roleType");

        if (roleType == 2) {    // 账号为Candidate
            CandidateUser candidateUser = commonService.getCandidateUserByUserAccount(userAccount);
            log.info("账号名为: {}的用户: {}查询个人信息成功！roleType: {}", userAccount, candidateUser.getUserName(), CommonConstants.roleTypeMap.get(roleType));
            return Result.success(candidateUser);
        }
        else if (roleType == 0 || roleType == 1) {  // 账号为公司内部人员：Admin/Interviewer
            InterviewerUser interviewerUser = commonService.getInterviewerUserByUserAccount(userAccount);
            log.info("账号名为: {}的用户: {}查询个人信息成功！roleType: {}", userAccount, interviewerUser.getUserName(), CommonConstants.roleTypeMap.get(roleType));
            return Result.success(interviewerUser);
        }
        else {
            //  TODO 后续其他身份类型开发...
            return null;
        }
    }


    /**
     * 修改个人基础信息接口
     * @param user
     * @return Result.success(newToken) / Result.error()
     */
    @PutMapping("/updateMyBasicUserInfo")
    public Result updateMyBasicUserInfo(@RequestBody @Validated(UpdateBasicUser.class) User user) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");

        if (!userAccount.equals(user.getUserAccount())) {
            log.info("账号名为: {}的用户: {}修改基础个人信息失败！修改时未合法指定自身账号！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("修改基础个人信息时未合法指定自身账号！");
        }
        else {
            commonService.updateBasicUser(user);
            log.info("账号名为: {}的用户: {}修改基础个人信息成功！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            // 销毁先前颁发的该账号token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String token = operations.get(userAccount);
            stringRedisTemplate.delete(userAccount);
            stringRedisTemplate.delete(token);
            // 依据修改后的基础个人信息生成新的token，并将其存储到redis缓存中
            User newUser = commonService.getUserByUserAccount(userAccount);
            String newToken = generateToken.generate(newUser.getUid(), newUser.getUserAccount(), newUser.getUserName(), newUser.getRoleType());
            operations.set(newToken, newToken, 1, TimeUnit.HOURS);
            operations.set(userAccount, newToken, 1, TimeUnit.HOURS);
            log.info("已为账号名为: {}的用户: {}重新生成token并销毁了先前的token！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.success(newToken);
        }
    }

    /**
     * 修改应聘者信息接口
     * @param candidate
     * @return Result.success() / Result.error()
     */
    @PutMapping("/updateMyCandidateInfo")
    public Result updateMyCandidateInfo(@RequestBody @Validated(UpdateCandidate.class) Candidate candidate) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        Integer roleType = (Integer) map.get("roleType");
        String userName = (String) map.get("userName");

        if (!userAccount.equals(candidate.getUserAccount())) {
            log.info("账号名为: {}的用户: {}修改应聘者信息失败！修改时未合法指定自身账号！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("修改应聘者信息时未合法指定自身账号！");
        }
        else if (roleType != 2) {
            log.info("账号名为: {}的用户: {}修改应聘者信息失败！该账号不是应聘者账号！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("非应聘者账号无法修改应聘者信息！");
        }
        else {
            commonService.updateCandidate(candidate);
            log.info("账号名为: {}的用户: {}修改应聘者信息成功！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.success();
        }
    }

    /**
     * 修改公司内部角色类型信息接口
     * @param interviewer
     * @return Result.success() / Result.error()
     */
    @PutMapping("/updateMyInterviewerInfo")
    public Result updateMyInterviewerInfo(@RequestBody @Validated(UpdateInterviewer.class) Interviewer interviewer) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        Integer roleType = (Integer) map.get("roleType");
        String userName = (String) map.get("userName");

        if (!userAccount.equals(interviewer.getUserAccount())) {
            log.info("账号名为: {}的用户: {}修改公司内部角色类型信息失败！修改时未合法指定自身账号！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("修改公司内部角色类型信息时未合法指定自身账号！");
        }
        else if (roleType != 0 && roleType != 1) {
            log.info("账号名为: {}的用户: {}修改公司内部角色类型信息失败！该账号不是公司内部角色类型账号！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("非公司内部角色类型账号无法修改公司内部角色类型信息！");
        }
        else {
            commonService.updateInterviewer(interviewer);
            log.info("账号名为: {}的用户: {}修改公司内部角色类型信息成功！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.success();
        }
    }

    /**
     * 修改用户密码接口 TODO 判断更新后的密码是否满足正则
     * @param params
     * @return Result.success() / Result.error()
     */
    @PatchMapping("/updateMyPwd")
    public Result updateMyPwd(@RequestParam Map<String, String> params) {
        //1.校验参数
        String oldPwd = params.get("oldPwd");
        String newPwd = params.get("newPwd");
        String rePwd = params.get("rePwd");
        if (!StringUtils.hasLength(oldPwd) || !StringUtils.hasLength(newPwd) || !StringUtils.hasLength(rePwd)) {
            log.info("修改密码失败！缺少必要的参数!");
            return Result.error("缺少必要的参数!");
        }
        //检查原密码是否正确
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");
        User originInfo = commonService.getUserByUserAccount(userAccount);
        String originPasswordHash = commonService.getPasswordHashByUserAccount(userAccount);
        if (!originPasswordHash.equals(Md5Util.getMD5String(oldPwd))) { // 输入的原始密码有误
            log.info("账号名为: {}的用户: {}修改密码失败！原密码填写不正确！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("原密码填写不正确!");
        }

        if (!rePwd.equals(newPwd)) {    // 新密码与确认密码不一致
            log.info("账号名为: {}的用户: {}修改密码失败！两次填写的新密码不一致！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
            return Result.error("两次填写的新密码不一致!");
        }

        // 此时原密码正确且新密码与确认密码一致，进行密码更新
        commonService.updateUserPwd(userAccount, newPwd);
        log.info("账号名为: {}的用户: {}修改密码成功！roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
        // 修改密码后，用户应重新登录，销毁先前颁发的token
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String token = operations.get(userAccount);
        stringRedisTemplate.delete(userAccount);
        stringRedisTemplate.delete(token);
        return Result.success();
    }

    /**
     * 上传文件至华为云OBS接口
     * @param file
     * @return Result.success(url)
     */
    @PostMapping("/upload")
    public Result upload(MultipartFile file) throws IOException {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        log.info("账号名为: {}的用户: {}进行文件上传，文件名: {}", userAccount, userName, file.getOriginalFilename());
        String url = huaWeiOBSUtils.upload(file);
        log.info("文件上传成功，文件URL: {}", url);
        return Result.success(url);
    }

}
