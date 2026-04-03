package com.klearn.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question")
@Data
@NoArgsConstructor
public class Question {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "expected_text", length = 500)
    private String expectedText;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    // Thêm vào bên trong class Question
    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY)
    private java.util.List<Answer> answers;
}
