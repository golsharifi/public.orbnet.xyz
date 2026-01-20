package com.orbvpn.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * OrbMesh Certificate Authority Service
 *
 * This service acts as a Certificate Authority for OrbMesh devices.
 * It signs device certificates during provisioning, allowing devices
 * to have valid TLS certificates without manual configuration.
 *
 * Security:
 * - CA private key should be stored securely (HSM in production)
 * - Certificates are tied to device identity
 * - Short-lived certificates with automatic renewal
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrbMeshCertificateAuthorityService {

    // CA certificate and key (loaded from configuration)
    @Value("${orbmesh.ca.certificate:}")
    private String caCertificatePem;

    @Value("${orbmesh.ca.private-key:}")
    private String caPrivateKeyPem;

    @Value("${orbmesh.ca.validity-days:365}")
    private int certificateValidityDays;

    private X509Certificate caCertificate;
    private PrivateKey caPrivateKey;
    private boolean caInitialized = false;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @PostConstruct
    public void init() {
        if (caCertificatePem != null && !caCertificatePem.isEmpty() &&
            caPrivateKeyPem != null && !caPrivateKeyPem.isEmpty()) {
            try {
                loadCA();
                caInitialized = true;
                log.info("✅ OrbMesh CA initialized successfully");
            } catch (Exception e) {
                log.warn("⚠️ Failed to initialize OrbMesh CA: {}. Certificate issuance will be disabled.", e.getMessage());
            }
        } else {
            log.info("ℹ️ OrbMesh CA not configured. Running generateCA() to create self-signed CA for development.");
            try {
                generateSelfSignedCA();
                caInitialized = true;
                log.info("✅ Generated self-signed CA for development");
            } catch (Exception e) {
                log.error("Failed to generate self-signed CA: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if CA is ready to issue certificates
     */
    public boolean isCAReady() {
        return caInitialized && caCertificate != null && caPrivateKey != null;
    }

    /**
     * Issue a certificate for a device
     *
     * @param deviceId The device identifier (used in CN)
     * @param publicIp The device's public IP (added as SAN)
     * @param hostname Optional hostname (added as SAN if provided)
     * @return DeviceCertificate containing cert, key, and CA cert
     */
    public DeviceCertificate issueCertificate(String deviceId, String publicIp, String hostname) throws Exception {
        if (!isCAReady()) {
            throw new IllegalStateException("CA is not initialized");
        }

        log.info("🔐 Issuing certificate for device: {} (IP: {})", deviceId, publicIp);

        // Generate key pair for the device
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair deviceKeyPair = keyGen.generateKeyPair();

        // Build certificate
        X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());
        X500Name subject = new X500Name("CN=" + deviceId + ",O=OrbMesh,OU=Devices");

        BigInteger serial = new BigInteger(128, new SecureRandom());
        Date notBefore = Date.from(Instant.now());
        Date notAfter = Date.from(Instant.now().plus(certificateValidityDays, ChronoUnit.DAYS));

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                deviceKeyPair.getPublic()
        );

        // Add extensions
        // Key Usage
        certBuilder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment)
        );

        // Extended Key Usage (TLS server + client)
        certBuilder.addExtension(
                Extension.extendedKeyUsage,
                false,
                new ExtendedKeyUsage(new KeyPurposeId[]{
                        KeyPurposeId.id_kp_serverAuth,
                        KeyPurposeId.id_kp_clientAuth
                })
        );

        // Subject Alternative Names
        GeneralNamesBuilder sanBuilder = new GeneralNamesBuilder();
        sanBuilder.addName(new GeneralName(GeneralName.iPAddress, publicIp));
        sanBuilder.addName(new GeneralName(GeneralName.dNSName, deviceId + ".orbmesh.local"));
        if (hostname != null && !hostname.isEmpty()) {
            sanBuilder.addName(new GeneralName(GeneralName.dNSName, hostname));
        }
        // Add common names for localhost access
        sanBuilder.addName(new GeneralName(GeneralName.dNSName, "localhost"));
        sanBuilder.addName(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));

        certBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                sanBuilder.build()
        );

        // Sign the certificate
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(caPrivateKey);

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate deviceCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        // Convert to PEM format
        String certPem = toPEM(deviceCert);
        String keyPem = toPEM(deviceKeyPair.getPrivate());
        String caCertPem = toPEM(caCertificate);

        log.info("✅ Certificate issued for device: {} (valid until: {})", deviceId, notAfter);

        return new DeviceCertificate(
                certPem,
                keyPem,
                caCertPem,
                serial.toString(),
                notBefore.toInstant().toString(),
                notAfter.toInstant().toString()
        );
    }

    /**
     * Get the CA certificate in PEM format (for clients to verify)
     */
    public String getCACertificatePem() throws Exception {
        if (!isCAReady()) {
            throw new IllegalStateException("CA is not initialized");
        }
        return toPEM(caCertificate);
    }

    /**
     * Generate a self-signed CA for development/testing
     */
    private void generateSelfSignedCA() throws Exception {
        log.info("🔧 Generating self-signed CA certificate...");

        // Generate CA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(4096, new SecureRandom());
        KeyPair caKeyPair = keyGen.generateKeyPair();

        // Build CA certificate
        X500Name caName = new X500Name("CN=OrbMesh Root CA,O=OrbNet,OU=Certificate Authority,C=US");
        BigInteger serial = new BigInteger(128, new SecureRandom());
        Date notBefore = Date.from(Instant.now());
        Date notAfter = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS)); // 10 years

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                caName,
                serial,
                notBefore,
                notAfter,
                caName,
                caKeyPair.getPublic()
        );

        // CA extensions
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
        );

        // Sign the CA certificate (self-signed)
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(caKeyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        this.caCertificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
        this.caPrivateKey = caKeyPair.getPrivate();

        // Log the CA cert for configuration
        log.info("📜 Self-signed CA certificate generated:");
        log.info("   Subject: {}", caCertificate.getSubjectX500Principal());
        log.info("   Valid until: {}", notAfter);
        log.debug("   CA Certificate PEM:\n{}", toPEM(caCertificate));
    }

    /**
     * Load CA certificate and private key from PEM strings
     */
    private void loadCA() throws Exception {
        // Parse CA certificate
        try (PEMParser parser = new PEMParser(new StringReader(caCertificatePem))) {
            Object obj = parser.readObject();
            if (obj instanceof X509CertificateHolder) {
                this.caCertificate = new JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate((X509CertificateHolder) obj);
            } else {
                throw new IllegalArgumentException("Invalid CA certificate format");
            }
        }

        // Parse CA private key
        try (PEMParser parser = new PEMParser(new StringReader(caPrivateKeyPem))) {
            Object obj = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            if (obj instanceof org.bouncycastle.openssl.PEMKeyPair) {
                this.caPrivateKey = converter.getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) obj).getPrivateKeyInfo());
            } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                this.caPrivateKey = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) obj);
            } else {
                throw new IllegalArgumentException("Invalid CA private key format");
            }
        }
    }

    /**
     * Convert an object to PEM format
     */
    private String toPEM(Object obj) throws IOException {
        StringWriter writer = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(obj);
        }
        return writer.toString();
    }

    /**
     * Device certificate bundle returned during provisioning
     */
    public record DeviceCertificate(
            String certificatePem,    // Device certificate (PEM)
            String privateKeyPem,     // Device private key (PEM)
            String caCertificatePem,  // CA certificate for verification (PEM)
            String serialNumber,      // Certificate serial number
            String validFrom,         // Certificate validity start
            String validUntil         // Certificate validity end
    ) {}
}
