package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.AttemptStatus;
import com.example.demo.repository.ExamAttemptRepository;
import com.example.demo.repository.ExamResultRepository;
import com.example.demo.repository.StudentAnswerRepository;
import com.example.demo.util.AnswerParser;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AttemptService {

    public static final String NO_ANSWER = "-";
    private static final List<AttemptStatus> ACTIVE =
            List.of(AttemptStatus.IN_PROGRESS, AttemptStatus.PENDING_CONFIRMATION);

    private final ExamAttemptRepository attemptRepo;
    private final ExamResultRepository resultRepo;
    private final StudentAnswerRepository answerRepo;

    public AttemptService(ExamAttemptRepository attemptRepo, ExamResultRepository resultRepo,
                          StudentAnswerRepository answerRepo) {
        this.attemptRepo = attemptRepo;
        this.resultRepo = resultRepo;
        this.answerRepo = answerRepo;
    }

    public static boolean isActive(ExamAttempt a) { return ACTIVE.contains(a.getStatus()); }

    public Optional<ExamAttempt> findById(Long id) { return attemptRepo.findById(id); }
    public Optional<ExamAttempt> findByStudentAndExam(User s, Exam e) { return attemptRepo.findByStudentAndExam(s, e); }
    public Optional<ExamAttempt> findActive(User s) {
        return attemptRepo.findFirstByStudentAndStatusInOrderByStartedAtDesc(s, ACTIVE);
    }
    public List<ExamAttempt> findOverdue() {
        return attemptRepo.findByStatusInAndDeadlineBefore(ACTIVE, LocalDateTime.now());
    }
    public Optional<ExamResult> findResult(ExamAttempt a) { return resultRepo.findByAttempt(a); }
    public Optional<ExamResult> findResultByAttemptId(Long id) { return resultRepo.findByAttemptId(id); }
    public List<ExamResult> resultsByStudent(User s) { return resultRepo.findByAttemptStudentOrderByCompletedAtDesc(s); }
    public List<ExamResult> resultsByExam(Exam e) {
        return resultRepo.findByAttemptExamOrderByCorrectAnswersDescCompletedAtAsc(e);
    }
    public List<StudentAnswer> answers(ExamAttempt a) { return answerRepo.findByAttemptOrderByQuestionNumber(a); }

    @Transactional
    public ExamAttempt start(User student, Exam exam) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt a = new ExamAttempt();
        a.setStudent(student);
        a.setExam(exam);
        a.setStatus(AttemptStatus.IN_PROGRESS);
        a.setStartedAt(now);
        a.setDeadline(now.plusMinutes(exam.getDurationMinutes()));
        return attemptRepo.save(a);
    }

    @Transactional
    public void saveDraft(Long attemptId, String draft) {
        ExamAttempt a = attemptRepo.findById(attemptId).orElseThrow();
        a.setDraftAnswers(draft);
        a.setStatus(AttemptStatus.PENDING_CONFIRMATION);
    }

    @Transactional
    public void reopen(Long attemptId) {
        attemptRepo.findById(attemptId).ifPresent(a -> a.setStatus(AttemptStatus.IN_PROGRESS));
    }

    /** Javoblarni tekshirib, natijani saqlaydi. Qayta chaqirilsa mavjud natijani qaytaradi. */
    @Transactional
    public ExamResult finish(Long attemptId) {
        ExamAttempt attempt = attemptRepo.findById(attemptId).orElseThrow();
        if (!isActive(attempt)) {
            return resultRepo.findByAttempt(attempt).orElseThrow();
        }

        Map<Integer, String> key = AnswerParser.parse(attempt.getExam().getAnswerKey());
        String draft = attempt.getDraftAnswers();
        boolean hasDraft = draft != null && !draft.isBlank();
        Map<Integer, String> given = hasDraft ? AnswerParser.parseStudent(draft) : Map.of();

        int correct = 0;
        List<StudentAnswer> rows = new ArrayList<>();
        for (Map.Entry<Integer, String> e : key.entrySet()) {
            String ans = given.get(e.getKey());
            boolean ok = ans != null && AnswerParser.matches(e.getValue(), ans);
            StudentAnswer sa = new StudentAnswer();
            sa.setAttempt(attempt);
            sa.setQuestionNumber(e.getKey());
            sa.setStudentAnswer(ans == null ? NO_ANSWER : ans);
            sa.setCorrect(ok);
            rows.add(sa);
            if (ok) correct++;
        }
        answerRepo.saveAll(rows);

        int total = key.size();
        ExamResult r = new ExamResult();
        r.setAttempt(attempt);
        r.setTotalQuestions(total);
        r.setCorrectAnswers(correct);
        r.setWrongAnswers(total - correct);
        r.setScorePercentage(Math.round(correct * 1000.0 / total) / 10.0);

        attempt.setStatus(hasDraft ? AttemptStatus.CONFIRMED : AttemptStatus.EXPIRED);
        attempt.setSubmittedAt(LocalDateTime.now());
        return resultRepo.save(r);
    }
}