package com.bracit.fisprocess.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecurityConfig JWT Decoder Tests")
class SecurityConfigJwtDecoderTest {

    @Test
    @DisplayName("should decode RS256 token with configured public key")
    void shouldDecodeRs256Token() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        KeyPair keyPair = rsaKeyPair();
        String pem = toPem((RSAPublicKey) keyPair.getPublic());
        JwtDecoder decoder = config.jwtDecoder(true, pem);

        Jwt decoded = decoder.decode(rs256Token("user-rsa", keyPair));
        assertThat(decoded.getSubject()).isEqualTo("user-rsa");
    }

    @Test
    @DisplayName("should fail when public key configuration is missing")
    void shouldFailWhenPublicKeyMissing() {
        SecurityConfig config = new SecurityConfig(null);
        assertThatThrownBy(() -> config.jwtDecoder(true, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public-key-pem must be configured");
    }

    @Test
    @DisplayName("should reject token signed by a different private key")
    void shouldRejectTokenWithWrongSigningKey() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        KeyPair keyPair = rsaKeyPair();
        String pem = toPem((RSAPublicKey) keyPair.getPublic());
        JwtDecoder decoder = config.jwtDecoder(true, pem);

        KeyPair wrongKeyPair = rsaKeyPair();
        String forgedToken = rs256Token("user-rsa", wrongKeyPair);

        assertThatThrownBy(() -> decoder.decode(forgedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("should reject expired token")
    void shouldRejectExpiredToken() throws Exception {
        SecurityConfig config = new SecurityConfig(null);
        KeyPair keyPair = rsaKeyPair();
        String pem = toPem((RSAPublicKey) keyPair.getPublic());
        JwtDecoder decoder = config.jwtDecoder(true, pem);
        String expiredToken = rs256Token("user-expired", keyPair, OffsetDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> decoder.decode(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    private String rs256Token(String subject, KeyPair keyPair) throws JOSEException {
        return rs256Token(subject, keyPair, OffsetDateTime.now().plusMinutes(30));
    }

    private String rs256Token(String subject, KeyPair keyPair, OffsetDateTime exp) throws JOSEException {
        JWTClaimsSet claims = claims(subject, exp);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(String subject, OffsetDateTime exp) {
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(new Date())
                .expirationTime(Date.from(exp.toInstant()))
                .claim("roles", List.of("FIS_ADMIN"))
                .build();
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String toPem(RSAPublicKey publicKey) {
        String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}
