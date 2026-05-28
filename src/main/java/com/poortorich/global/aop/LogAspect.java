package com.poortorich.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Slf4j
@Component
public class LogAspect {

    @Pointcut("within(com.poortorich..facade..*) || within(com.poortorich..service..*)")
    public void facadeAndService() {
    }

    @Pointcut("execution(* com.poortorich..controller..*(..))")
    public void controller() {
    }

    @Around("facadeAndService()")
    public Object logFacadeAndService(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithMethodLogging(joinPoint);
    }

    @Around("controller()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("--- Request Start ---");
        try {
            logRequestInfo();
            return executeWithMethodLogging(joinPoint);
        } finally {
            log.info("--- Request End ---");
        }
    }

    private Object executeWithMethodLogging(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.info("[START] [{}] {}", className, methodName);

        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            log.error("[ERROR] [{}] {} : {}", className, methodName, e.getMessage());
            throw e;
        } finally {
            long timeInMs = System.currentTimeMillis() - start;
            log.info("[END] [{}] {} | {}ms", className, methodName, timeInMs);
        }
    }

    private void logRequestInfo() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        log.info("[REQUEST] [{}] {}", request.getMethod(), request.getRequestURI());
        log.info("[PARAMS] {}", getParams(request));
        log.info("[IP] {}", request.getRemoteAddr());
    }

    private Map<String, String> getParams(HttpServletRequest request) {
        Map<String, String> paramMap = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String param = params.nextElement();
            paramMap.put(param.replaceAll("\\.", "-"), request.getParameter(param));
        }
        return paramMap;
    }
}
