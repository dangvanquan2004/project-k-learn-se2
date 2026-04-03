package com.klearn.service;

import com.klearn.dto.LessonCompletionResponse;
import com.klearn.model.*;
import com.klearn.repository.LessonRepository;
import com.klearn.repository.LessonResultRepository;
import com.klearn.repository.ExerciseRepository;
import com.klearn.repository.UserRepository;
import com.klearn.repository.UserAnswerRepository;
import com.klearn.repository.QuestionRepository;
import com.klearn.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonCompletionService {

    private final LessonRepository lessonRepository;
    private final LessonResultRepository lessonResultRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final ExerciseRepository exerciseRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;

    private final XpService xpService;
    private final StreakService streakService;
    private final BadgeService badgeService;

    // Check if lesson can be marked completed and if yes, compute scores and persist LessonResult + XP + badges.
    @Transactional
    public LessonCompletionResponse tryCompleteLesson(Long userId, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        if (lesson == null) {
            LessonCompletionResponse resp = new LessonCompletionResponse();
            resp.setCompleted(false);
            resp.setNewlyEarnedBadges(List.of());
            return resp;
        }

        var user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            LessonCompletionResponse resp = new LessonCompletionResponse();
            resp.setCompleted(false);
            resp.setNewlyEarnedBadges(List.of());
            return resp;
        }

        // If lesson already completed and lesson_result exists, don't re-award XP/badges.
        Optional<LessonResult> already = lessonResultRepository.findByUser_UserIdAndLesson_LessonId(userId, lessonId);
        if (already.isPresent()) {
            LessonCompletionResponse resp = new LessonCompletionResponse();
            resp.setCompleted(true);
            resp.setTotalScore(already.get().getTotalScore());
            resp.setXpEarned(already.get().getXpEarned());
            resp.setNewlyEarnedBadges(List.of());
            return resp;
        }

        List<Exercise> exercises = exerciseRepository.findByLesson_LessonId(lessonId);
        if (exercises.isEmpty()) {
            LessonCompletionResponse resp = new LessonCompletionResponse();
            resp.setCompleted(false);
            resp.setNewlyEarnedBadges(List.of());
            return resp;
        }

        List<Question> allQuestions = new ArrayList<>();
        for (Exercise ex : exercises) {
            allQuestions.addAll(questionRepository.findByExercise_ExerciseId(ex.getExerciseId()));
        }
        if (allQuestions.isEmpty()) {
            LessonCompletionResponse resp = new LessonCompletionResponse();
            resp.setCompleted(false);
            resp.setNewlyEarnedBadges(List.of());
            return resp;
        }

        Set<Long> totalQuestionIds = allQuestions.stream()
            .map(Question::getQuestionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<UserAnswer> userAnswers = userAnswerRepository.findByUser_UserIdAndQuestion_Exercise_Lesson_LessonId(userId, lessonId);

        // Choose late  st submission per question_id (so retry doesn't keep old wrong answers forever).
        Map<Long, UserAnswer> latestByQuestionId = userAnswers.stream()
            .filter(ua -> ua.getQuestion() != null && ua.getQuestion().getQuestionId() != null)
            .collect(Collectors.toMap(
                ua -> ua.getQuestion().getQuestionId(),
                ua -> ua,
                (a, b) -> {
                    LocalDateTime at = a.getSubmittedAt();
                    LocalDateTime bt = b.getSubmittedAt();
                    if (at == null && bt == null) return a;
                    if (at == null) return b;
                    if (bt == null) return a;
                    return at.isAfter(bt) ? a : b;
                }
            ));

        Set<Long> answeredQuestionIds = latestByQuestionId.keySet();
        if (!answeredQuestionIds.containsAll(totalQuestionIds)) {
            LessonCompletionResponse resp = new LessonCompletionResponse();
            resp.setCompleted(false);
            resp.setNewlyEarnedBadges(List.of());
            return resp;
        }

        // Compute scores per exercise type.
        Map<Exercise.ExerciseType, List<Question>> questionsByType = allQuestions.stream()
            .filter(q -> q.getExercise() != null && q.getExercise().getType() != null)
            .collect(Collectors.groupingBy(q -> q.getExercise().getType()));

        Map<Exercise.ExerciseType, Integer> scoreByType = new EnumMap<>(Exercise.ExerciseType.class);

        for (Map.Entry<Exercise.ExerciseType, List<Question>> entry : questionsByType.entrySet()) {
            Exercise.ExerciseType type = entry.getKey();
            List<Question> qs = entry.getValue();

            if (type == Exercise.ExerciseType.speaking) {
                BigDecimal sum = BigDecimal.ZERO;
                int count = 0;
                for (Question q : qs) {
                    UserAnswer ua = latestByQuestionId.get(q.getQuestionId());
                    if (ua != null && ua.getPronunciationScore() != null) {
                        sum = sum.add(ua.getPronunciationScore());
                        count++;
                    }
                }
                int avg = count > 0 ? sum.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValue() : 0;
                scoreByType.put(type, avg);
            } else {
                int correct = 0;
                for (Question q : qs) {
                    UserAnswer ua = latestByQuestionId.get(q.getQuestionId());
                    if (ua != null && Boolean.TRUE.equals(ua.getIsCorrect())) {
                        correct++;
                    }
                }
                int total = qs.size();
                int pct = total > 0 ? (int) Math.round(((double) correct / (double) total) * 100.0) : 0;
                scoreByType.put(type, pct);
            }
        }

        int readingScore = scoreByType.getOrDefault(Exercise.ExerciseType.reading, 0);
        int listeningScore = scoreByType.getOrDefault(Exercise.ExerciseType.listening, 0);
        int speakingScore = scoreByType.getOrDefault(Exercise.ExerciseType.speaking, 0);
        int writingScore = scoreByType.getOrDefault(Exercise.ExerciseType.writing, 0);

        int totalScore = (readingScore + listeningScore + speakingScore + writingScore) / 4;
        int xpEarned = lesson.getXpReward() != null ? lesson.getXpReward() : 0;

        LessonResult lr = new LessonResult();
        lr.setUser(user);
        lr.setLesson(lesson);
        lr.setReadingScore(readingScore);
        lr.setListeningScore(listeningScore);
        lr.setSpeakingScore(speakingScore);
        lr.setWritingScore(writingScore);
        lr.setTotalScore(totalScore);
        lr.setXpEarned(xpEarned);
        lr.setCompletedAt(LocalDateTime.now());

        // Persist once.
        lessonResultRepository.save(lr);

        // Update user_progress.
        UserProgress progress = userProgressRepository.findByUser_UserIdAndLesson_LessonId(userId, lessonId).orElseGet(() -> {
            UserProgress p = new UserProgress();
            p.setUser(user);
            p.setLesson(lesson);
            return p;
        });
        progress.setStatus("completed");
        progress.setScore(totalScore);
        userProgressRepository.save(progress);

        // Update streak first (it triggers streak badge check).
        streakService.updateStreak(userId);

        // Add XP + level, then award all remaining badges (lesson_complete/xp_milestone/perfect_speaking).
        xpService.addXp(userId, xpEarned);
        List<Badge> newlyEarned = badgeService.checkAndAwardBadgesAndReturn(userId);

        LessonCompletionResponse resp = new LessonCompletionResponse();
        resp.setCompleted(true);
        resp.setTotalScore(totalScore);
        resp.setXpEarned(xpEarned);
        resp.setNewlyEarnedBadges(newlyEarned);
        return resp;
    }
}

