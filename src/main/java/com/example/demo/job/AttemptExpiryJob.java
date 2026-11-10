package com.example.demo.job;

import com.example.demo.bot.ExamTelegramBot;
import com.example.demo.entity.ExamAttempt;
import com.example.demo.entity.ExamResult;
import com.example.demo.service.AttemptService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttemptExpiryJob {

    private final AttemptService attemptService;
    private final ExamTelegramBot bot;

    public AttemptExpiryJob(AttemptService attemptService, ExamTelegramBot bot) {
        this.attemptService = attemptService;
        this.bot = bot;
    }

    @Scheduled(fixedDelay = 15_000)
    public void closeOverdue() {
        for (ExamAttempt a : attemptService.findOverdue()) {
            try {
                ExamResult r = attemptService.finish(a.getId());
                bot.notifyTimeUp(a.getStudent().getTelegramId(), r);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}