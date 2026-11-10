package com.example.demo.bot;

public enum BotState {
    NONE,
    // Kanal biriktirish
    WAITING_CHANNEL,
    // Test yaratish
    WAITING_TEST_TITLE,
    WAITING_TEST_FILE,
    WAITING_TEST_ANSWERS,
    // Imtihonni boshlash
    WAITING_EXAM_DURATION,
    // O'quvchi javob yuborishi
    WAITING_STUDENT_ANSWERS
}