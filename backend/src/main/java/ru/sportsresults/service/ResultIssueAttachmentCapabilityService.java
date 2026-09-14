package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.config.ResultIssueAttachmentProperties;
import ru.sportsresults.domain.ResultIssueRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ResultIssueAttachmentCapabilityService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ResultIssueAttachmentProperties properties;

    public ResultIssueAttachmentCapabilityService(ResultIssueAttachmentProperties properties) {
        this.properties = properties;
    }

    public IssuedCapability issueFor(ResultIssueRequest issue) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = Instant.now().plus(properties.issueTokenTtl());
        issue.setAttachmentUploadTokenHash(hash(token));
        issue.setAttachmentUploadTokenExpiresAt(expiresAt);
        return new IssuedCapability(token, expiresAt);
    }

    public boolean permits(ResultIssueRequest issue, String token) {
        if (token == null || token.isBlank()
                || issue.getAttachmentUploadTokenHash() == null
                || issue.getAttachmentUploadTokenExpiresAt() == null
                || !Instant.now().isBefore(issue.getAttachmentUploadTokenExpiresAt())) {
            return false;
        }
        byte[] expected = issue.getAttachmentUploadTokenHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(token.strip()).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedCapability(String token, Instant expiresAt) {
    }
}
