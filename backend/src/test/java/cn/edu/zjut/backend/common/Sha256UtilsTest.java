package cn.edu.zjut.backend.common;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256UtilsTest {

    @Test
    void shouldCalculateKnownSha256() throws Exception {
        String hash = Sha256Utils.hash(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldCalculateEmptySha256() throws Exception {
        String hash = Sha256Utils.hash(new ByteArrayInputStream(new byte[0]));

        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
