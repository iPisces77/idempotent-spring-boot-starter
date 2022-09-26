package com.ipisces42.idempotent.aspect;

import com.ipisces42.idempotent.annotation.Idempotent;
import com.ipisces42.idempotent.exception.IdempotentException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Aspect
public class IdempotentAspect {

  public static final ReentrantLock lock = new ReentrantLock();
  private static final Logger LOG = LoggerFactory.getLogger(IdempotentAspect.class);
  private static final ThreadLocal<Map<String, Object>> THREAD_CACHE = ThreadLocal.withInitial(
      HashMap::new);
  private static final String RMAP_KEY = "idempotent";
  @Autowired
  private Redisson redisson;
  @Autowired
  private HttpServletRequest request;

  @Pointcut("@annotation(com.ipisces42.idempotent.annotation.Idempotent)")
  public void pointCut() {

  }

  @Before("pointCut()")
  public void before(JoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    if (!method.isAnnotationPresent(Idempotent.class)) {
      return;
    }
    Idempotent idempotent = method.getAnnotation(Idempotent.class);
    String url = request.getRequestURL().toString();
    String argString = Arrays.toString(joinPoint.getArgs());

    String key = url + argString;

    int expireTime = idempotent.expireTime();
    String message = idempotent.message();
    TimeUnit timeUnit = idempotent.timeUnit();

    RMapCache<String, Object> mapCache = redisson.getMapCache(RMAP_KEY);
    String value = LocalDateTime.now().toString().replace("T", " ");

    if (Objects.nonNull(mapCache.get(key))) {
      throw new IdempotentException(message);
    }

    synchronized (this) {
      Object obj = mapCache.putIfAbsent(key, value, expireTime, timeUnit);
      if (Objects.isNull(obj)) {
        throw new IdempotentException(message);
      } else {
        LOG.info("[idempotent]:has stored key={},value={},expireTime={}{},now={}", key, value,
            expireTime,
            timeUnit, LocalDateTime.now());
      }
    }
    Map<String, Object> map = THREAD_CACHE.get();
    map.put("key", key);

  }

  @After("pointCut()")
  public void after(JoinPoint joinPoint) {
    Map<String, Object> map = THREAD_CACHE.get();
    if (map.isEmpty()) {
      return;
    }
    RMapCache<Object, Object> mapCache = redisson.getMapCache(RMAP_KEY);
    if (mapCache.isEmpty()) {
      return;
    }
    mapCache.remove(map.get("key").toString());
    THREAD_CACHE.remove();
  }
}
