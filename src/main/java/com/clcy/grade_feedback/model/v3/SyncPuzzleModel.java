package com.clcy.grade_feedback.model.v3;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncPuzzleModel implements Serializable {

    @Getter
    @Setter
    private String puzzleIdx;

    @Getter
    @Setter
    private String answer;

    /**
     * 这道题作答完成后点击下一题的时间, 系统赋值
     * */
    @Getter
    @Setter
    private Long clickNextTime;
}
