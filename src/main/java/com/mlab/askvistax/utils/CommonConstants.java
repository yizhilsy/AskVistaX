package com.mlab.askvistax.utils;

import java.util.HashMap;
import java.util.Map;

public class CommonConstants {
    public static Map<Integer, String> roleTypeMap = new HashMap<>();

    static {
        roleTypeMap.put(0, "Admin");
        roleTypeMap.put(1, "Interviewer");
        roleTypeMap.put(2, "Candidate");
    }
}
