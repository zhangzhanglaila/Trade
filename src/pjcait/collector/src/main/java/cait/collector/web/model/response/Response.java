package cait.collector.web.model.response;

import cait.collector.web.data.code.ServiceCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 通用的响应
 *
 * @param code 响应码
 * @param msg  响应信息
 * @param data 响应数据
 * @param <T>  data的类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Response<T>(int code, String msg, T data) {

    public static <T> Response<T> success() {
        return success(null);
    }

    public static <T> Response<T> success(T data) {
        return new Response<>(ServiceCode.Ok.code, "ok", data);
    }

    public static <T> Response<T> success(int code, T data) {
        return new Response<>(code, "ok", data);
    }

    public static <T> Response<T> success(int code, String msg, T data) {
        return new Response<>(code, msg, data);
    }

    public static <T> Response<T> error(ServiceCode code) {
        return new Response<>(code.code, code.msg, null);
    }

    public static <T> Response<T> error(int code) {
        return new Response<>(code, null, null);
    }

    public static <T> Response<T> error(int code, String message) {
        return new Response<>(code, message, null);
    }

    public static <T> Response<T> error(ServiceCode code, String message) {
        return new Response<>(code.code, message, null);
    }
}
