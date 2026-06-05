package com.aichat.auth.web;

import com.aichat.auth.AuthSession;
import com.aichat.auth.model.User;
import com.aichat.auth.model.WechatAccount;
import com.aichat.auth.repo.WechatAccountRepository;
import com.aichat.auth.repo.UserRepository;
import com.aichat.auth.wechat.WechatOAuthClient;
import com.aichat.auth.wechat.WechatProperties;
import com.aichat.auth.web.dto.MeResponse;
import com.aichat.auth.web.dto.WechatMockConfirmRequest;
import com.aichat.auth.web.dto.WechatQrResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Duration STATE_TTL = Duration.ofMinutes(2);

    private final UserRepository userRepository;
    private final WechatAccountRepository wechatAccountRepository;
    private final WechatProperties wechatProperties;
    private final WechatOAuthClient wechatOAuthClient;

    /**
     * Stage-A only: in-memory state store.
     * Key: state, Value: expiresAt epochMillis.
     */
    private final Map<String, Long> stateStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthController(
            UserRepository userRepository,
            WechatAccountRepository wechatAccountRepository,
            WechatProperties wechatProperties,
            WechatOAuthClient wechatOAuthClient
    ) {
        this.userRepository = userRepository;
        this.wechatAccountRepository = wechatAccountRepository;
        this.wechatProperties = wechatProperties;
        this.wechatOAuthClient = wechatOAuthClient;
    }

    @GetMapping("/wechat/qr")
    public WechatQrResponse wechatQr() {
        String state = generateState();
        long expiresAt = Instant.now().plus(STATE_TTL).toEpochMilli();
        stateStore.put(state, expiresAt);
        String qrUrl = buildQrConnectUrl(state);
        return new WechatQrResponse(state, qrUrl, STATE_TTL.getSeconds());
    }

    @GetMapping("/wechat/callback")
    public ResponseEntity<Void> wechatCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpSession session
    ) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Long expiresAt = stateStore.remove(state);
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String appId = wechatProperties.getAppId();
        String appSecret = wechatProperties.getAppSecret();
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        WechatOAuthClient.WechatTokenResponse token = wechatOAuthClient.exchangeCodeForToken(appId, appSecret, code);
        if (!token.ok()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        WechatOAuthClient.WechatUserInfoResponse profile = wechatOAuthClient.fetchUserInfo(token.accessToken(), token.openId());

        String openId = token.openId();
        String unionId = token.unionId();
        String nickname = profile.ok() ? profile.nickname() : null;
        String avatar = profile.ok() ? profile.avatarUrl() : null;
        String rawProfile = profile.ok() && profile.raw() != null ? profile.raw().toString() : null;

        Instant now = Instant.now();
        String userId = wechatAccountRepository.findByOpenId(openId)
                .map(WechatAccount::getUserId)
                .orElseGet(() -> {
                    String newUserId = UUID.randomUUID().toString();
                    userRepository.save(new User(newUserId, now, now));
                    wechatAccountRepository.save(new WechatAccount(
                            newUserId,
                            openId,
                            unionId,
                            nickname,
                            avatar,
                            "snsapi_login",
                            rawProfile,
                            now
                    ));
                    return newUserId;
                });

        // Update profile best-effort if already exists
        wechatAccountRepository.findByOpenId(openId).ifPresent(acc -> {
            acc.updateProfile(unionId, nickname, avatar, "snsapi_login", rawProfile, now);
            wechatAccountRepository.save(acc);
        });

        AuthSession.login(session, userId);

        String redirect = safePostLoginRedirect();
        URI redirectUri = UriComponentsBuilder.fromUriString(redirect).build(true).toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }

    /**
     * Stage-A only: simulate "scan confirm" from WeChat.
     * Frontend calls this endpoint after user clicks a mock button.
     */
    @PostMapping("/wechat/mock/confirm")
    public ResponseEntity<MeResponse> wechatMockConfirm(
            @RequestBody(required = false) WechatMockConfirmRequest request,
            HttpSession session
    ) {
        String state = request == null ? null : request.getState();
        if (state == null || state.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Long expiresAt = stateStore.remove(state);
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Instant now = Instant.now();
        String userId = UUID.randomUUID().toString();
        userRepository.save(new User(userId, now, now));
        AuthSession.login(session, userId);

        return ResponseEntity.ok(new MeResponse(true, userId));
    }

    @GetMapping("/me")
    public MeResponse me(HttpSession session) {
        Object userId = session == null ? null : session.getAttribute(AuthSession.USER_ID_KEY);
        if (userId instanceof String s && !s.isBlank()) {
            return new MeResponse(true, s);
        }
        return new MeResponse(false, null);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        if (session != null) {
            AuthSession.logout(session);
        }
        return ResponseEntity.noContent().build();
    }

    private String generateState() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildQrConnectUrl(String state) {
        String appId = wechatProperties.getAppId();
        String callbackUrl = wechatProperties.getCallbackUrl();
        if (appId == null || appId.isBlank() || callbackUrl == null || callbackUrl.isBlank()) {
            // Fallback for local Stage-A: frontend can still use mock confirm.
            return null;
        }

        return UriComponentsBuilder
                .fromHttpUrl("https://open.weixin.qq.com/connect/qrconnect")
                .queryParam("appid", appId)
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_login")
                .queryParam("state", state)
                .build(true)
                .toUriString() + "#wechat_redirect";
    }

    private String safePostLoginRedirect() {
        String redirect = wechatProperties.getPostLoginRedirectUrl();
        if (redirect == null || redirect.isBlank()) {
            return "http://localhost:5174/";
        }
        // Minimal safety: only allow http(s) URLs (no javascript: etc)
        String lower = redirect.trim().toLowerCase();
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return "http://localhost:5174/";
        }
        return redirect;
    }
}

