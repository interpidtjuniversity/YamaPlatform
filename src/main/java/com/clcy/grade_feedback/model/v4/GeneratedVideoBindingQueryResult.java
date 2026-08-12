package com.clcy.grade_feedback.model.v4;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query result that explicitly distinguishes a missing conversation from an empty binding tree.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedVideoBindingQueryResult {

    private Boolean found;
    private String message;
    private GeneratedVideoBindingTreeModel bindingTree;
}
