package com.klearn.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "exercise")
@Data
@NoArgsConstructor
public class Exercise {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exercise_id")
    private Long exerciseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExerciseType type;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;
    @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY)
    private List<Question> questions;
    public enum ExerciseType {
        listening, speaking, reading, writing
    }
}
