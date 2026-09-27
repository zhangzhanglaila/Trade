package cait.collector.web.data.code;

public enum ServiceCode {
    Ok(0, "ok"),
    UnknownErr(-1, "未知错误"),

    ApiNotImplement(-2, "api不存在或未开放/已弃用，请检查app版本"),
    ParamWrong(-3, "参数错误"),
    RequestInvalid(-4, "请求无效"),
    PermissionDenied(-5, "权限不正确"),

    UserAuthErr(100_0000, "登录状态无效或已过期"),
    OidcCodeAuthWrong(100_0001, "oidc code 无效"),

    ActivityEnded(101_0000, "活动已结束"),

    DataConflict(102_0000, "数据冲突"),
    DataDuplicate(102_0001, "数据重复"),
    DataFormatWrong(102_0002, "数据格式错误"),
    DataNotExists(102_0003, "数据不存在"),
    DataNotMatch(102_0004, "数据不匹配"),

    BaseJwcFetch(103_0000, "wusthelper api 请求错误"),

    UserNotExists(104_0000, "用户不存在"),
    UserPasswordWrong(104_0001, "用户密码错误"),
    StuNumDoesNotExists(104_0002, "学号不存在"),

    WusthelperApiRequestErr(105_0000, "wusthelper请求错误")
    ;

    public final int code;
    public final String msg;

    ServiceCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static ServiceCode of(int code) {
        for (ServiceCode serviceCode : ServiceCode.values()) {
            if (serviceCode.code == code) {
                return serviceCode;
            }
        }

        return null;
    }
}
