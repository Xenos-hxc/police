package com.railway.security.shared.web;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBind(Exception ex) {
        var binding =
                ex instanceof MethodArgumentNotValidException e
                        ? e.getBindingResult()
                        : ((BindException) ex).getBindingResult();
        var fieldError = binding.getFieldError();
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.fail(
                                400,
                                fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求数据格式错误"));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "缺少必填请求参数"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求参数类型错误"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(413, "上传文件超过大小限制"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "上传请求格式错误"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(404, "请求接口不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(405, "请求方法不支持"));
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleDenied() {
        return ApiResponse.fail(403, "无权访问该数据或执行该操作");
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationException.class)
    public ApiResponse<Void> handleAuthentication() {
        return ApiResponse.fail(401, "账号或密码错误");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception ex) {
        log.error("Unhandled server error", ex);
        return ApiResponse.fail(500, "系统内部错误，请联系管理员");
    }
}
