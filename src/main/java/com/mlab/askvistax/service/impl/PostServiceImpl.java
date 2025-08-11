package com.mlab.askvistax.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mlab.askvistax.mapper.PostMapper;
import com.mlab.askvistax.pojo.DeliveryPost;
import com.mlab.askvistax.pojo.PageBean;
import com.mlab.askvistax.pojo.Post;
import com.mlab.askvistax.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostServiceImpl implements PostService {
    @Autowired
    private PostMapper postMapper;

    @Override
    public PageBean listPosts(Integer page, Integer pageSize, String postName, List<String> postLocation, List<String> postBusinessGroup,
                              List<Integer> postType, List<Integer> postCategory) {
        PageHelper.startPage(page, pageSize);
        List<Post> postList = postMapper.listPosts(postName, postLocation, postBusinessGroup, postType, postCategory);
        Page<Post> p = (Page<Post>) postList;
        PageBean pageBean = new PageBean(p.getTotal(), p.getResult());
        return pageBean;
    }

    @Override
    public Post getPostByPostId(Integer postId) {
        return postMapper.getPostByPostId(postId);
    }

    public void deliveryPost(String uid, Integer postId, String resumeUrl) {
        postMapper.deliveryPost(uid, postId, resumeUrl);
    }

    @Override
    public List<DeliveryPost> llMyDeliveryPosts(String uid) {
        return postMapper.llMyDeliveryPosts(uid);
    }
}
