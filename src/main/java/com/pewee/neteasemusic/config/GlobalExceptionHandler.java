package com.pewee.neteasemusic.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.exceptions.ServiceException;
import com.pewee.neteasemusic.models.common.RespEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器
 * 
 * @author pewee
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 拦截非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleIllegalArgumentException(IllegalArgumentException e,
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生非法参数异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.NOT_LEGAL_PARAM);
        entity.setMsg("参数错误: " + e.getMessage());
        return entity;
    }
    
    /**
     * 非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleIllegalStateException(IllegalStateException e,
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生非法状态异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.SERVICE_EXECUTION_ERROR);
        entity.setMsg("状态错误: " + e.getMessage());
        return entity;
    }
    
    /**
     * 拦截参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
        HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生参数校验异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> respEntity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.NOT_LEGAL_PARAM);
        String errorMessage = e.getBindingResult()
            .getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        return respEntity.setMsg("参数校验失败: " + errorMessage);
    }
    
    /**
     * validated校验的参数异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleConstraintViolationException(ConstraintViolationException e, 
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生参数约束异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.NOT_LEGAL_PARAM);
        entity.setMsg("参数约束失败: " + e.getMessage());
        return entity;
    }

    /**
     * 参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生参数类型不匹配异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.NOT_LEGAL_PARAM);
        entity.setMsg("参数类型错误: " + e.getName() + " 应为 " + e.getRequiredType().getSimpleName());
        return entity;
    }

    /**
     * 参数缺失异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleMissingServletRequestParameterException(MissingServletRequestParameterException e,
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生参数缺失异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.NOT_LEGAL_PARAM);
        entity.setMsg("缺少必需参数: " + e.getParameterName());
        return entity;
    }

    /**
     * IO异常
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RespEntity<String> handleIOException(IOException e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生IO异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.SYS_ERROR);
        entity.setMsg("文件操作失败: " + e.getMessage());
        return entity;
    }

    /**
     * URI语法异常
     */
    @ExceptionHandler(URISyntaxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespEntity<String> handleURISyntaxException(URISyntaxException e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生URI语法异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.NOT_LEGAL_PARAM);
        entity.setMsg("URL格式错误: " + e.getMessage());
        return entity;
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RespEntity<String> handleServiceException(ServiceException e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生业务异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(e);
        if (e.getMessage() != null && !e.getMessage().isEmpty()) {
            entity.setMsg(e.getMessage());
        }
        return entity;
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RespEntity<String> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生运行时异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.SERVICE_EXECUTION_ERROR);
        entity.setMsg("运行时错误: " + e.getMessage());
        return entity;
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RespEntity<String> handleException(Exception e, HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常: {}", requestUri, e.getMessage(), e);
        RespEntity<String> entity = RespEntity.<String>applyRespCodeMsg(CommonRespInfo.SYS_ERROR);
        entity.setMsg("系统错误: " + e.getMessage());
        return entity;
    }
}
