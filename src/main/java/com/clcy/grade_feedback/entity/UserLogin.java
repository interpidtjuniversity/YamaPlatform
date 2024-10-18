package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class UserLogin {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private String password;

    @Getter
    @Setter
    private String studentName;
}
