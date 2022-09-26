package com.ipisces42.idempotent.aspect;

import com.ipisces42.idempotent.annotation.Idempotent;
import com.ipisces42.idempotent.exception.IdempotentException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
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
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Aspect
public class IdempotentAspect {

  public static final ReentrantLock lock = new ReentrantLock();
  private static final Logger LOG = LoggerFactory.getLogger(IdempotentAspect.class);
  private static final String RMAP_KEY = "idempotent";
  @Autowired
  private RedissonClient redisson;
  @Autowired
  private HttpServletRequest request;

  private static Idempotent getIdempotent(JoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    if (!method.isAnnotationPresent(Idempotent.class)) {
      return null;
    }
    return method.getAnnotation(Idempotent.class);
  }

  @Pointcut("@annotation(com.ipisces42.idempotent.annotation.Idempotent)")
  public void pointCut() {

  }

  @Before("pointCut()")
  public void before(JoinPoint joinPoint) {
    Idempotent idempotent = getIdempotent(joinPoint);
    if (idempotent == null) {
      return;
    }
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
      }
    }
  }

  @After("pointCut()")
  public void after(JoinPoint joinPoint) {

    Idempotent idempotent = getIdempotent(joinPoint);
    if (idempotent == null) {
      return;
    }
    String argString = Arrays.toString(joinPoint.getArgs());
    String url = request.getRequestURL().toString();
    boolean afterDel = idempotent.afterDel();
    String key = url + argString;
    RMapCache<Object, Object> mapCache = redisson.getMapCache(RMAP_KEY);
    if (mapCache.isEmpty()) {
      return;
    }
    if (afterDel) {
      mapCache.remove(key);
    }
  }
}
