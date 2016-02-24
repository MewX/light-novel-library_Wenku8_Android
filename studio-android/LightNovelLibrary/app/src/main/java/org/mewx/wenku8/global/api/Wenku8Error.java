package org.mewx.wenku8.global.api;

/**
 * Created by MewX on 2015/6/12.
 * Save error list, interpret each error.
 */
public class Wenku8Error {
    public enum ErrorCode {
        // default unknown
        ERROR_DEFAULT,
        // system defined
        SYSTEM_0_REQUEST_ERROR,
        SYSTEM_1_SUCCEEDED,
        SYSTEM_2_ERROR_USERNAME,
        SYSTEM_3_ERROR_PASSWORD,
        SYSTEM_4_NOT_LOGGED_IN,
        SYSTEM_5_ALREADY_IN_BOOKSHELF,
        SYSTEM_6_BOOKSHELF_FULL,
        SYSTEM_7_NOVEL_NOT_IN_BOOKSHELF,
        SYSTEM_8_TOPIC_NOT_EXIST,
        SYSTEM_9_SIGN_FAILED,
        SYSTEM_10_RECOMMEND_FAILED,
        SYSTEM_11_POST_FAILED,
        SYSTEM_22_REFER_PAGE_0,
        // custom
        RETURNED_VALUE_EXCEPTION,
        BYTE_TO_STRING_EXCEPTION,
        USER_INFO_EMPTY,
        NETWORK_ERROR,
        STRING_CONVERSION_ERROR,
        XML_PARSE_FAILED,
        USER_CANCELLED_TASK,
        PARAM_COUNT_NOT_MATCHED,
        LOCAL_BOOK_REMOVE_FAILED,
        SERVER_RETURN_NOTHING
    }

    public static String translateErrorCode(ErrorCode ec) {

        return "";
    }

    public static ErrorCode getSystemDefinedErrorCode(int err) {
        /*
        0 请求发生错误
        1 成功(登陆、添加、删除、发帖)
        2 用户名错误
        3 密码错误
        4 请先登陆
        5 已经在书架
        6 书架已满
        7 小说不在书架
        8 回复帖子主题不存在
        9 签到失败
        10 推荐失败
        11 帖子发送失败
        22 refer page 0
        */

        switch(err) {
            case 0:
                return ErrorCode.SYSTEM_0_REQUEST_ERROR;
            case 1:
                return ErrorCode.SYSTEM_1_SUCCEEDED;
            case 2:
                return ErrorCode.SYSTEM_2_ERROR_USERNAME;
            case 3:
                return ErrorCode.SYSTEM_3_ERROR_PASSWORD;
            case 4:
                return ErrorCode.SYSTEM_4_NOT_LOGGED_IN;
            case 5:
                return ErrorCode.SYSTEM_5_ALREADY_IN_BOOKSHELF;
            case 6:
                return ErrorCode.SYSTEM_6_BOOKSHELF_FULL;
            case 7:
                return ErrorCode.SYSTEM_7_NOVEL_NOT_IN_BOOKSHELF;
            case 8:
                return ErrorCode.SYSTEM_8_TOPIC_NOT_EXIST;
            case 9:
                return ErrorCode.SYSTEM_9_SIGN_FAILED;
            case 10:
                return ErrorCode.SYSTEM_10_RECOMMEND_FAILED;
            case 11:
                return ErrorCode.SYSTEM_11_POST_FAILED;
            case 22:
                return ErrorCode.SYSTEM_22_REFER_PAGE_0;
            default:
                return ErrorCode.ERROR_DEFAULT;
        }
    }
}
