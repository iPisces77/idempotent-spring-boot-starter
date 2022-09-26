package com.ipisces42.idempotent;

import com.ipisces42.idempotent.aspect.IdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author fuhaixin
 *
 **/
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class IdempotentAutoConfiguration {

  @Bean
  public IdempotentAspect idempotentAspect() {
    return new IdempotentAspect();
  }
}
