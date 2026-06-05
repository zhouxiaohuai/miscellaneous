package com.aichat.auth.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aichat.wechat")
public class WechatProperties {
    /**
     * WeChat Open Platform appid for QRConnect.
     */
    private String appId;

    /**
     * WeChat Open Platform secret for QRConnect.
     */
    private String appSecret;

    /**
     * Callback URL configured in WeChat Open Platform (must match exactly).
     */
    private String callbackUrl;

    /**
     * Frontend URL to redirect after successful login.
     */
    private String postLoginRedirectUrl;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getPostLoginRedirectUrl() {
        return postLoginRedirectUrl;
    }

    public void setPostLoginRedirectUrl(String postLoginRedirectUrl) {
        this.postLoginRedirectUrl = postLoginRedirectUrl;
    }
}

