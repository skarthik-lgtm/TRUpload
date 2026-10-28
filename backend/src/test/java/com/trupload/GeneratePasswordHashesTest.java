
package com.trupload;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GeneratePasswordHashesTest {

    @Test
    void generatePasswordHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String brijeshHash = encoder.encode("BDimri123");
        String krishnaHash = encoder.encode("SKrishna123");

        System.out.println("Brijesh hash: " + brijeshHash);
        System.out.println("Brijesh hash length: " + brijeshHash.length());

        System.out.println("Sri Krishna hash: " + krishnaHash);
        System.out.println("Sri Krishna hash length: " + krishnaHash.length());
    }
}
