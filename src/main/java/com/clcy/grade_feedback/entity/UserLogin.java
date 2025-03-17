package com.clcy.grade_feedback.entity;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    @Getter
    @Setter
    private String role;
}
