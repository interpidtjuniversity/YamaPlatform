package com.clcy.grade_feedback.model.v4;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Business result returned after saving generated video bindings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedVideoBindingSaveResult {

    private Boolean success;
    private String message;
    private Integer savedCount;
}
