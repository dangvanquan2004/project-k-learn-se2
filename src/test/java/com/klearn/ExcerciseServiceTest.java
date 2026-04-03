package com.klearn;


import com.klearn.service.ExerciseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExerciseServiceTest {

    private ExerciseService exerciseService;

    @BeforeEach
    void setUp() {
        // Chỉ khởi tạo đối tượng trực tiếp vì không cần dùng Mockito trong bài test này
        exerciseService = new ExerciseService(null, null, null, null, null, null);
    }

    @Test
    void calculateSpeakingScore_ExactMatch() {
        String expected = "안녕하세요";
        String result = "안녕하세요";

        BigDecimal score = exerciseService.calculateSpeakingScore(expected, result);

        assertEquals(new BigDecimal("100.00"), score);
    }

    @Test
    void calculateSpeakingScore_WithExtraWhitespaces() {
        String expected = "안녕하세요";
        String result = "  안녕하세요   "; // Có khoảng trắng thừa

        BigDecimal score = exerciseService.calculateSpeakingScore(expected, result);

        assertEquals(new BigDecimal("100.00"), score);
    }

    @Test
    void calculateSpeakingScore_PartialMatch() {
        // Levenshtein: "abc" và "ab" có khoảng cách là 1. Độ dài max là 3.
        // Tỷ lệ đúng = (1 - 1/3) * 100 = 66.67
        String expected = "abc";
        String result = "ab";

        BigDecimal score = exerciseService.calculateSpeakingScore(expected, result);

        assertEquals(new BigDecimal("66.67"), score);
    }

    @Test
    void calculateSpeakingScore_CompletelyDifferent() {
        String expected = "abc";
        String result = "xyz";

        // Khoảng cách là 3, độ dài max là 3. Tỷ lệ = 0.
        BigDecimal score = exerciseService.calculateSpeakingScore(expected, result);

        assertEquals(new BigDecimal("0.00"), score);
    }

    @Test
    void calculateSpeakingScore_NullInputs() {
        BigDecimal score1 = exerciseService.calculateSpeakingScore(null, "test");
        BigDecimal score2 = exerciseService.calculateSpeakingScore("test", null);
        BigDecimal score3 = exerciseService.calculateSpeakingScore(null, null);

        assertEquals(new BigDecimal("0.00"), score1);
        assertEquals(new BigDecimal("0.00"), score2);
        assertEquals(new BigDecimal("0.00"), score3);
    }
}
