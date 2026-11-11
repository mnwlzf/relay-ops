package com.relayops.upstream.adapter.in.web;

import com.relayops.shared.error.BusinessException;
import com.relayops.shared.error.ResourceNotFoundException;
import com.relayops.shared.web.ApiResponse;
import com.relayops.upstream.application.collection.CollectionService;
import com.relayops.upstream.application.collection.UpstreamStore;
import com.relayops.upstream.application.security.CredentialCipher;
import com.relayops.upstream.domain.AccountSnapshot;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API of the collection slice. */
@RestController
@RequestMapping("/api/v1/upstream")
public class UpstreamController {

    private final UpstreamStore store;
    private final CollectionService collectionService;
    private final CredentialCipher cipher;

    public UpstreamController(UpstreamStore store, CollectionService collectionService,
                              CredentialCipher cipher) {
        this.store = store;
        this.collectionService = collectionService;
        this.cipher = cipher;
    }

    @GetMapping("/platforms")
    public ApiResponse<List<UpstreamStore.PlatformRow>> platforms() {
        return ApiResponse.of(store.platforms());
    }

    @PostMapping("/platforms")
    public ApiResponse<UpstreamStore.PlatformRow> createPlatform(@RequestBody PlatformRequest request) {
        PlatformType type = PlatformType.parse(request.type());
        if (request.code() == null || request.code().isBlank()) {
            throw new BusinessException("PLATFORM_INVALID", "平台 code 不能为空");
        }
        long id = store.insertPlatform(request.code().strip(), request.name(), type,
                trimSlash(request.baseUrl()), request.enabled() == null || request.enabled());
        return ApiResponse.of(new UpstreamStore.PlatformRow(id, request.code(), request.name(), type,
                request.baseUrl(), request.enabled() == null || request.enabled()));
    }

    /** Account list; never includes the credential. */
    @GetMapping("/accounts")
    public ApiResponse<List<AccountView>> accounts() {
        return ApiResponse.of(store.accounts().stream().map(AccountView::of).toList());
    }

    @PostMapping("/accounts")
    public ApiResponse<AccountView> createAccount(@RequestBody AccountRequest request) {
        UpstreamStore.PlatformRow platform = store.findPlatform(request.platformCode())
                .orElseThrow(() -> new ResourceNotFoundException("PLATFORM_NOT_FOUND",
                        "平台不存在: " + request.platformCode()));
        String encrypted = cipher.encrypt(request.credential());
        long id = store.insertAccount(platform.id(), request.name(), request.login(), encrypted,
                CredentialCipher.hint(request.credential()), request.enabled() == null || request.enabled());
        UpstreamAccount account = store.findAccount(id).orElseThrow();
        return ApiResponse.of(AccountView.of(account));
    }

    @PostMapping("/collect")
    public ApiResponse<CollectionService.Summary> collect() {
        return ApiResponse.of(collectionService.collectAll("MANUAL"));
    }

    @GetMapping("/accounts/{id}/snapshots")
    public ApiResponse<List<AccountSnapshot>> snapshots(@PathVariable long id,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.of(store.recentSnapshots(id, limit));
    }

    @GetMapping("/runs")
    public ApiResponse<List<UpstreamStore.RunRow>> runs(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.of(store.recentRuns(limit));
    }

    /** Latest snapshot per enabled account, ordered by balance: the first dashboard view. */
    @GetMapping("/dashboard")
    public ApiResponse<List<UpstreamStore.DashboardRow>> dashboard(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.of(store.dashboard(limit));
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("PLATFORM_INVALID", "平台 baseUrl 不能为空");
        }
        String trimmed = url.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** Platform create payload. */
    public record PlatformRequest(String code, String name, String type, String baseUrl, Boolean enabled) {
    }

    /** Account create payload; {@code credential} is encrypted before it reaches the database. */
    public record AccountRequest(String platformCode, String name, String login, String credential,
                                 Boolean enabled) {
    }

    /** Account view without the credential. */
    public record AccountView(Long id, String platformCode, String platformType, String name, String login,
                              String credentialHint, boolean enabled, String lastCollectStatus,
                              String lastCollectMessage, String lastCollectAt) {

        public static AccountView of(UpstreamAccount account) {
            return new AccountView(account.id(), account.platformCode(), account.platformType().name(),
                    account.name(), account.login(), account.credentialHint(), account.enabled(),
                    account.lastCollectStatus() == null ? null : account.lastCollectStatus().name(),
                    account.lastCollectMessage(),
                    account.lastCollectAt() == null ? null : account.lastCollectAt().toString());
        }
    }
}
