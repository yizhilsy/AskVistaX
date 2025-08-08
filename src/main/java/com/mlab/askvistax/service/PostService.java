package com.mlab.askvistax.service;

import com.mlab.askvistax.pojo.PageBean;
import com.mlab.askvistax.pojo.Post;

import java.util.List;

public interface PostService {
    PageBean listPosts(Integer page, Integer pageSize, String postName, List<String> postLocation, List<String> postBusinessGroup,
                       List<Integer> postType, List<Integer> postCategory);

    Post getPostByPostId(Integer postId);
}
