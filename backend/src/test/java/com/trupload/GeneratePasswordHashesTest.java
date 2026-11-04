
package com.trupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GeneratePasswordHashesTest {

    @Test
    void generatePasswordHashes() {
        String firstPassword = System.getenv("BOOTSTRAP_PASSWORD_1");
        String secondPassword = System.getenv("BOOTSTRAP_PASSWORD_2");
        Assumptions.assumeTrue(firstPassword != null && secondPassword != null,
                "Set BOOTSTRAP_PASSWORD_1 and BOOTSTRAP_PASSWORD_2 to generate hashes.");

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode(firstPassword));
        System.out.println(encoder.encode(secondPassword));
    }
}
