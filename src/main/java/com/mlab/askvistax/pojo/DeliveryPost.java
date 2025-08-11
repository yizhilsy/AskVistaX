package com.mlab.askvistax.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class DeliveryPost {
    private Integer interviewId;
    private Integer postId;
    private String postName;
    private String postDescription;
    private String postRequirement;
    private String postNote;
    private String postLocation;
    private String postBusinessGroup;
    private Integer postType;
    private Integer postCategory;
}
