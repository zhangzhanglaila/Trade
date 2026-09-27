package cait.collector.web.handler;

import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.exception.ServiceException;
import cait.collector.web.model.response.Response;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ResponseBody
    @ExceptionHandler(ServiceException.class)
    public Response<Object> baseException(ServiceException e) {
        return Response.error(e.getCodeValue(), e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Response<Object> baseException(Exception e) {
        log.error("未处理的异常：", e);
        return Response.error(ServiceCode.UnknownErr);
    }

    /**
     * 404
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(NoHandlerFoundException.class)
    public Response<Object> baseException(NoHandlerFoundException e) {
        return Response.error(ServiceCode.ApiNotImplement);
    }

    /**
     * 处理参数不完整的请求异常
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Response<Object> handler(MissingServletRequestParameterException e) {
        log.debug("请求的参数不完整: " + e);
        return Response.error(ServiceCode.ParamWrong);
    }

    /**
     * 处理参数不完整的请求异常
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(BindException.class)
    public Response<Object> handler(BindException e) {
        log.debug("请求的参数不完整: " + e);
        return Response.error(ServiceCode.ParamWrong);
    }

    /**
     * 处理参数类型错误的请求异常（请求参数类型错误）
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Response<Object> handler(MethodArgumentTypeMismatchException e) {
        log.debug(String.format("请求错误（%s）: %s", e.getClass().getName(), e));
        return Response.error(ServiceCode.ParamWrong);
    }

    /**
     * 处理参数类型错误的请求异常2（Json解析错误）
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(JsonMappingException.class)
    public Response<Object> handler(JsonMappingException e) {
        log.debug(String.format("请求错误（%s）: %s", e.getClass().getName(), e));
        return Response.error(ServiceCode.ParamWrong);
    }

    /**
     * 处理参数类型错误的请求异常3（字段映射错误）
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<Object> handler(HttpMessageNotReadableException e) {
        log.debug(String.format("请求错误（%s）: %s", e.getClass().getName(), e));
        return Response.error(ServiceCode.ParamWrong);
    }

    /**
     * 处理请求头中“Content-Type”字段不正确的异常
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Response<Object> handler(HttpMediaTypeNotSupportedException e) {
        log.debug(String.format("请求错误（%s）: %s", e.getClass().getName(), e));
        return Response.error(ServiceCode.ParamWrong, "不接受的Content-Type类型: %s".formatted(e));
    }

    /**
     * 处理请求方法错误的情况
     *
     * @param e 异常
     * @return 统一响应
     */
    @ResponseBody
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Response<Object> handler(HttpRequestMethodNotSupportedException e) {
        log.debug(String.format("请求错误（%s）: %s", e.getClass().getName(), e));
        return Response.error(ServiceCode.RequestInvalid);
    }
}
