package cait.collector.web.exception;

import cait.collector.web.data.code.ServiceCode;

public class ServiceException extends RuntimeException {
    private final int code;

    public ServiceException(ServiceCode code) {
        this(code, code.msg);
    }

    public ServiceException(ServiceCode code, String message) {
        super(message);
        this.code = code.code;
    }

    public ServiceException(int code) {
        this(code, "");
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceCode getCode() {
        return ServiceCode.of(code);
    }

    public int getCodeValue() {
        return code;
    }

    public static ServiceException error(int code) throws ServiceException {
        return new ServiceException(code);
    }

    public static ServiceException error(ServiceCode code) throws ServiceException {
        return new ServiceException(code);
    }

    public static ServiceException error(ServiceCode code, String msg) throws ServiceException {
        return new ServiceException(code, msg);
    }

    public static ServiceException error(int code, String msg) throws ServiceException {
        return new ServiceException(code, msg);
    }
}
