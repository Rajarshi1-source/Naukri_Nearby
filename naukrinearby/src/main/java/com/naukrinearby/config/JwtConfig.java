package com.naukrinearby.config;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 signing keys for stateless JWTs (security-and-api.md §A.2). If no keys are configured the
 * app generates an ephemeral keypair at startup — fine for local dev, NOT for production (tokens
 * become invalid on restart). Supply PEM PKCS#8 private + X.509 public keys via env for production.
 */
@Slf4j
@Configuration
public class JwtConfig {

	@Bean
	RSAKey rsaKey(JwtProperties props) {
		RSAPublicKey publicKey;
		RSAPrivateKey privateKey;
		if (hasText(props.privateKey()) && hasText(props.publicKey())) {
			publicKey = parsePublicKey(props.publicKey());
			privateKey = parsePrivateKey(props.privateKey());
			log.info("Loaded RS256 JWT keys from configuration");
		}
		else {
			KeyPair pair = generateKeyPair();
			publicKey = (RSAPublicKey) pair.getPublic();
			privateKey = (RSAPrivateKey) pair.getPrivate();
			log.warn("No JWT keys configured — generated an EPHEMERAL RS256 keypair (dev only)");
		}
		return new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
	}

	@Bean
	JwtEncoder jwtEncoder(RSAKey rsaKey) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	@Bean
	JwtDecoder jwtDecoder(RSAKey rsaKey) throws Exception {
		return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to generate RSA keypair", ex);
		}
	}

	private static RSAPublicKey parsePublicKey(String pem) {
		try {
			byte[] der = Base64.getDecoder().decode(stripPem(pem));
			return (RSAPublicKey) KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(der));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Invalid RSA public key", ex);
		}
	}

	private static RSAPrivateKey parsePrivateKey(String pem) {
		try {
			byte[] der = Base64.getDecoder().decode(stripPem(pem));
			return (RSAPrivateKey) KeyFactory.getInstance("RSA")
					.generatePrivate(new PKCS8EncodedKeySpec(der));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Invalid RSA private key", ex);
		}
	}

	private static String stripPem(String pem) {
		return pem.replace("\\n", "")
				.replaceAll("-----BEGIN (.*)-----", "")
				.replaceAll("-----END (.*)-----", "")
				.replaceAll("\\s", "");
	}
}
