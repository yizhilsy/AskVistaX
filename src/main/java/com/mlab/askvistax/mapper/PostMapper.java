package com.mlab.askvistax.mapper;

import com.mlab.askvistax.pojo.DeliveryPost;
import com.mlab.askvistax.pojo.Post;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostMapper {
    List<Post> listPosts(String postName, List<String> postLocation, List<String> postBusinessGroup,
                         List<Integer> postType, List<Integer> postCategory);

    Post getPostByPostId(Integer postId);

    void deliveryPost(String uid, Integer postId, String resumeUrl);

    List<DeliveryPost> llMyDeliveryPosts(String uid);
}
