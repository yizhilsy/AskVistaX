package com.mlab.askvistax.service;

import com.mlab.askvistax.pojo.PageBean;

import java.util.List;

public interface PostService {
    PageBean listPosts(Integer page, Integer pageSize, String postName, List<String> postLocation, List<String> postBusinessGroup, List<Integer> postType);
}
