package com.aichat.auth.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "wechat_accounts", indexes = {
        @Index(name = "idx_wechat_openid", columnList = "open_id", unique = true),
        @Index(name = "idx_wechat_unionid", columnList = "union_id")
})
public class WechatAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "open_id", nullable = false, length = 64)
    private String openId;

    @Column(name = "union_id", length = 64)
    private String unionId;

    @Column(name = "nickname", length = 255)
    private String nickname;

    @Column(name = "avatar", length = 1024)
    private String avatar;

    @Column(name = "scope", length = 64)
    private String scope;

    @Lob
    @Column(name = "raw_profile")
    private String rawProfile;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WechatAccount() {}

    public WechatAccount(String userId, String openId, String unionId, String nickname, String avatar, String scope, String rawProfile, Instant updatedAt) {
        this.userId = userId;
        this.openId = openId;
        this.unionId = unionId;
        this.nickname = nickname;
        this.avatar = avatar;
        this.scope = scope;
        this.rawProfile = rawProfile;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getOpenId() {
        return openId;
    }

    public String getUnionId() {
        return unionId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getScope() {
        return scope;
    }

    public String getRawProfile() {
        return rawProfile;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(String unionId, String nickname, String avatar, String scope, String rawProfile, Instant now) {
        this.unionId = unionId;
        this.nickname = nickname;
        this.avatar = avatar;
        this.scope = scope;
        this.rawProfile = rawProfile;
        this.updatedAt = now;
    }
}

