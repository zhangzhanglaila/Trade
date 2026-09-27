package com.example.tdproject.utils;

import lombok.Data;

/**
 * 统一返回结果封装类
 * 所有接口返回数据均使用此格式，便于前端统一处理
 *
 * @param <T> 数据泛型（支持任意类型的返回数据）
 */
@Data
public class Result<T> {

    // 返回码（200：成功，其他：失败）
    private Integer code;

    // 返回消息（成功/失败描述）
    private String message;

    // 返回数据（成功时携带具体数据，失败时可为null）
    private T data;

    // 私有化构造方法，禁止外部直接实例化
    private Result() {}

    /**
     * 构建成功结果（带数据）
     *
     * @param data 返回的数据
     * @param <T>  数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> build(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCodeEnum.SUCCESS.getCode());
        result.setMessage(ResultCodeEnum.SUCCESS.getMessage());
        if (data != null) {
            result.setData(data);
        }
        return result;
    }

    /**
     * 构建结果（根据状态枚举）
     *
     * @param resultCodeEnum 状态枚举（包含code和message）
     * @param <T>            数据类型
     * @return 自定义状态的Result对象
     */
    public static <T> Result<T> build(ResultCodeEnum resultCodeEnum) {
        Result<T> result = new Result<>();
        result.setCode(resultCodeEnum.getCode());
        result.setMessage(resultCodeEnum.getMessage());
        return result;
    }

    /**
     * 构建结果（带状态枚举和自定义消息）
     *
     * @param resultCodeEnum 状态枚举
     * @param message        自定义消息
     * @param <T>            数据类型
     * @return 自定义状态和消息的Result对象
     */
    public static <T> Result<T> build(ResultCodeEnum resultCodeEnum, String message) {
        Result<T> result = new Result<>();
        result.setCode(resultCodeEnum.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 成功返回（无数据）
     */
    public static <T> Result<T> success() {
        return build(ResultCodeEnum.SUCCESS);
    }

    /**
     * 成功返回（带消息）
     */
    public static <T> Result<T> success(String message) {
        return build(ResultCodeEnum.SUCCESS, message);
    }

    /**
     * 成功返回（带数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCodeEnum.SUCCESS.getCode());
        result.setMessage(ResultCodeEnum.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 失败返回（默认消息）
     */
    public static <T> Result<T> error() {
        return build(ResultCodeEnum.FAIL);
    }

    /**
     * 失败返回（自定义消息）
     */
    public static <T> Result<T> error(String message) {
        return build(ResultCodeEnum.FAIL, message);
    }
}