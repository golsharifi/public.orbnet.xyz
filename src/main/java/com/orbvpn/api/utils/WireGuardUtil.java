// src/main/java/com/orbvpn/api/utils/WireGuardUtil.java

package com.orbvpn.api.utils;

import lombok.Data;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class WireGuardUtil {

    @Data
    public static class KeyPair {
        private final String privateKey;
        private final String publicKey;
    }

    /**
     * Generate WireGuard key pair using wg command
     * Requires wireguard-tools to be installed on the system
     */
    public static KeyPair generateKeyPair() {
        try {
            // Generate private key using ProcessBuilder
            ProcessBuilder genKeyBuilder = new ProcessBuilder("wg", "genkey");
            Process genKeyProcess = genKeyBuilder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(genKeyProcess.getInputStream()));
            String privateKey = reader.readLine();

            if (privateKey == null) {
                throw new RuntimeException("Failed to generate private key - no output from wg genkey");
            }

            privateKey = privateKey.trim();
            genKeyProcess.waitFor();

            if (genKeyProcess.exitValue() != 0) {
                throw new RuntimeException("wg genkey failed with exit code: " + genKeyProcess.exitValue());
            }

            // Generate public key from private key using ProcessBuilder
            ProcessBuilder pubKeyBuilder = new ProcessBuilder("wg", "pubkey");
            Process pubKeyProcess = pubKeyBuilder.start();

            // Write private key to stdin of wg pubkey
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(pubKeyProcess.getOutputStream()))) {
                writer.write(privateKey);
                writer.newLine();
                writer.flush();
            }

            reader = new BufferedReader(
                    new InputStreamReader(pubKeyProcess.getInputStream()));
            String publicKey = reader.readLine();

            if (publicKey == null) {
                throw new RuntimeException("Failed to generate public key - no output from wg pubkey");
            }

            publicKey = publicKey.trim();
            pubKeyProcess.waitFor();

            if (pubKeyProcess.exitValue() != 0) {
                throw new RuntimeException("wg pubkey failed with exit code: " + pubKeyProcess.exitValue());
            }

            return new KeyPair(privateKey, publicKey);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate WireGuard keys. Ensure wireguard-tools is installed.", e);
        }
    }

    /**
     * Increment IP address (for IP pool allocation)
     * Example: 10.8.0.2 -> 10.8.0.3
     */
    public static String incrementIP(String ip) {
        String[] octets = ip.split("\\.");

        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IP address format: " + ip);
        }

        int lastOctet = Integer.parseInt(octets[3]);

        lastOctet++;

        if (lastOctet > 254) {
            // Move to next subnet
            int thirdOctet = Integer.parseInt(octets[2]);
            thirdOctet++;
            if (thirdOctet > 255) {
                throw new RuntimeException("IP pool exhausted");
            }
            octets[2] = String.valueOf(thirdOctet);
            octets[3] = "2"; // Start from .2 (skip .0 and .1 which are reserved)
        } else {
            octets[3] = String.valueOf(lastOctet);
        }

        return String.join(".", octets);
    }

    public static boolean isValidPublicKey(String publicKey) {
        if (publicKey == null || publicKey.length() != 44) {
            return false;
        }
        // WireGuard keys are 32 bytes base64-encoded = 44 characters
        try {
            java.util.Base64.getDecoder().decode(publicKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}