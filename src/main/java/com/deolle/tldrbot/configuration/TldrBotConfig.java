package com.deolle.tldrbot.configuration;

import com.deolle.tldrbot.service.TelegramService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TldrBotConfig {

  @Bean
  public TelegramService getTelegramService() {
    return new org.telegram.service.TelegramService();
  }
}
