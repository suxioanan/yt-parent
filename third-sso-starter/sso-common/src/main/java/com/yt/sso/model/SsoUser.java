package com.yt.sso.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * SSO 统一用户信息模型
 *
 * @author sunan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoUser {

    /** 平台用户ID */
    private String userId;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 性别 */
    private String gender;

    /** 头像URL */
    private String avatar;

    /** 统一ID（钉钉独有，微信为 null） */
    private String unionId;


    /** 工号 */
    private String jobNumber;

    /** 全局唯一。对于同一个服务商，不同应用获取到企业内同一个成员的open_userid是相同的，最多64个字节。仅第三方应用可获取。 */
    private String openId;

    /** 地址。 */
    private String address;

    /** 来源平台：dingtalk / wechat */
    private String source;

    /** 平台特有字段（部门、职位等，不会丢数据） */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}
