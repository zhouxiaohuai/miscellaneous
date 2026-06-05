package com.aichat.auth.wechat;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Component
public class WechatOAuthClient {
    private final RestTemplate restTemplate;

    public WechatOAuthClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public WechatTokenResponse exchangeCodeForToken(String appId, String appSecret, String code) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build(true)
                .toUri();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>) (Map<?, ?>) restTemplate.getForObject(uri, Map.class);
        if (resp == null) return WechatTokenResponse.error("Empty response from WeChat");

        Object errCode = resp.get("errcode");
        Object errMsg = resp.get("errmsg");
        if (errCode != null) {
            return WechatTokenResponse.error("WeChat token exchange failed: " + errCode + " " + errMsg);
        }

        return new WechatTokenResponse(
                asString(resp.get("access_token")),
                asString(resp.get("openid")),
                asString(resp.get("unionid")),
                null
        );
    }

    public WechatUserInfoResponse fetchUserInfo(String accessToken, String openId) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/userinfo")
                .queryParam("access_token", accessToken)
                .queryParam("openid", openId)
                .queryParam("lang", "zh_CN")
                .build(true)
                .toUri();

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>) (Map<?, ?>) restTemplate.getForObject(uri, Map.class);
        if (resp == null) return WechatUserInfoResponse.error("Empty response from WeChat");

        Object errCode = resp.get("errcode");
        Object errMsg = resp.get("errmsg");
        if (errCode != null) {
            return WechatUserInfoResponse.error("WeChat userinfo fetch failed: " + errCode + " " + errMsg);
        }

        return new WechatUserInfoResponse(
                asString(resp.get("openid")),
                asString(resp.get("unionid")),
                asString(resp.get("nickname")),
                asString(resp.get("headimgurl")),
                resp,
                null
        );
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public record WechatTokenResponse(String accessToken, String openId, String unionId, String error) {
        static WechatTokenResponse error(String error) {
            return new WechatTokenResponse(null, null, null, error);
        }

        public boolean ok() {
            return error == null && accessToken != null && !accessToken.isBlank() && openId != null && !openId.isBlank();
        }
    }

    public record WechatUserInfoResponse(
            String openId,
            String unionId,
            String nickname,
            String avatarUrl,
            Map<String, Object> raw,
            String error
    ) {
        static WechatUserInfoResponse error(String error) {
            return new WechatUserInfoResponse(null, null, null, null, null, error);
        }

        public boolean ok() {
            return error == null && openId != null && !openId.isBlank();
        }
    }
}

