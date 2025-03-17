package com.clcy.grade_feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GradeFeedbackApplication {

    public static void main(String[] args) {
        SpringApplication.run(GradeFeedbackApplication.class, args);
    }

}
