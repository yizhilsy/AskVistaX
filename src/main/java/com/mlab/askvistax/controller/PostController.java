package com.mlab.askvistax.controller;

import com.mlab.askvistax.pojo.DeliveryPost;
import com.mlab.askvistax.pojo.PageBean;
import com.mlab.askvistax.pojo.Post;
import com.mlab.askvistax.pojo.Result;
import com.mlab.askvistax.service.PostService;
import com.mlab.askvistax.utils.CommonConstants;
import com.mlab.askvistax.utils.HuaWeiOBSUtils;
import com.mlab.askvistax.utils.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/post")
@Validated
@Slf4j
public class PostController {
    @Autowired
    private PostService postService;

    @GetMapping("/listPosts")
    public Result listPosts(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer pageSize,
                            @RequestParam(required = false) String postName, @RequestParam(required = false) List<String> postLocation,
                            @RequestParam(required = false) List<String> postBusinessGroup, @RequestParam(required = false) List<Integer> postType,
                            @RequestParam(required = false) List<Integer> postCategory) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");
        PageBean pageBean = postService.listPosts(page, pageSize, postName, postLocation, postBusinessGroup, postType, postCategory);
        log.info("账号名为: {}的用户: {}分页查询Posts成功, roleType: {}, 参数page: {}, pageSize: {}, postName: {}, postLocation: {}, postBusinessGroup: {}, postType: {}, postCategory: {}",
                userAccount, userName, CommonConstants.roleTypeMap.get(roleType), page, pageSize, postName, postLocation, postBusinessGroup, postType, postCategory);
        return Result.success(pageBean);
    }

    @GetMapping("/getPostByPostId")
    public Result getPostByPostId(@RequestParam Integer postId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");
        Post post = postService.getPostByPostId(postId);
        log.info("账号名为: {}的用户: {}查询Post成功, roleType: {}, postId: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType), postId);
        return Result.success(post);
    }

    @PostMapping("/deliveryPost")
    public Result deliveryPost( @RequestParam Integer postId, @RequestParam String resumeUrl) throws IOException {
        Map<String, Object> map = ThreadLocalUtil.get();
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");
        String uid = (String) map.get("uid");
        postService.deliveryPost(uid, postId, resumeUrl);
        log.info("账号名为: {}的用户: {}投递post成功, roleType: {}, postId: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType), postId);
        return Result.success();

    }

    @GetMapping("/llMyDeliveryPosts")
    public Result llMyDeliveryPosts() {
        Map<String, Object> map = ThreadLocalUtil.get();
        String uid = (String) map.get("uid");
        String userAccount = (String) map.get("userAccount");
        String userName = (String) map.get("userName");
        Integer roleType = (Integer) map.get("roleType");
        List<DeliveryPost> deliveryPostList = postService.llMyDeliveryPosts(uid);
        log.info("账号名为: {}的用户: {}查询自己的投递记录成功, roleType: {}", userAccount, userName, CommonConstants.roleTypeMap.get(roleType));
        return Result.success(deliveryPostList);
    }

}
