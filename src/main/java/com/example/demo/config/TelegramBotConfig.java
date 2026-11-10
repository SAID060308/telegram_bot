
package com.example.demo.config;

import com.example.demo.bot.ExamTelegramBot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramBotConfig {

    @Bean
    public TelegramClient telegramClient(Environment environment) {

        String botToken = environment.getRequiredProperty(
                "telegram.bot.token"
        );

        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication(
            Environment environment,
            ExamTelegramBot examTelegramBot) throws Exception {

        String botToken = environment.getRequiredProperty(
                "telegram.bot.token"
        );

        TelegramBotsLongPollingApplication application =
                new TelegramBotsLongPollingApplication();

        application.registerBot(botToken, examTelegramBot);

        return application;
    }
}
