package com.clcy.grade_feedback.model.v3;

import lombok.*;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncExamModel implements Serializable {

    // 元信息
    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private int groupId;

    @Getter
    @Setter
    private String examName;

    // 剩余时间, 只在返回时赋值
    @Getter
    @Setter
    private Long remainMillSeconds;

    // 后端设置并计算
    @Getter
    @Setter
    private Date startTime;

    @Getter
    @Setter
    private Date endTime;
}
