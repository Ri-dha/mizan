package iq.mizan.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import iq.mizan.common.exception.ApiException;

import org.junit.jupiter.api.Test;

class ReportImagesTest {

    static byte[] encode(String format, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    /** A JPEG as a phone writes it: an EXIF block with the location right after the start marker. */
    static byte[] jpegWithLocation() throws IOException {
        byte[] plain = encode("jpg", 40, 30);
        byte[] payload = "Exif\0\0GPS 33.3152N 44.3661E".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((payload.length + 2) >> 8);
        out.write((payload.length + 2) & 0xFF);
        out.write(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    static byte[] pngWithText() throws IOException {
        byte[] plain = encode("png", 20, 10);
        byte[] chunk = "tEXtComment\0GPS 33.3152N".getBytes(StandardCharsets.ISO_8859_1);
        int iend = plain.length - 12;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, iend);
        int length = chunk.length - 4;
        out.write(new byte[] {(byte) (length >> 24), (byte) (length >> 16), (byte) (length >> 8), (byte) length});
        out.write(chunk);
        out.write(new byte[4]);
        out.write(plain, iend, 12);
        return out.toByteArray();
    }

    @Test
    void jpegLosesItsLocationAndStillDecodes() throws IOException {
        ReportImages.Clean clean = ReportImages.clean(jpegWithLocation());

        assertThat(clean.contentType()).isEqualTo("image/jpeg");
        assertThat(new String(clean.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS");
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(clean.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(40);
    }

    @Test
    void pngLosesItsTextAndStillDecodes() throws IOException {
        ReportImages.Clean clean = ReportImages.clean(pngWithText());

        assertThat(clean.contentType()).isEqualTo("image/png");
        assertThat(new String(clean.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("GPS");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(clean.bytes())).getHeight()).isEqualTo(10);
    }

    @Test
    void otherFilesDamagedImagesAndHugeSidesAreRefused() throws IOException {
        assertThatThrownBy(() -> ReportImages.clean("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isInstanceOf(ApiException.class);
        byte[] jpeg = encode("jpg", 40, 30);
        assertThatThrownBy(() -> ReportImages.clean(java.util.Arrays.copyOf(jpeg, 20))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ReportImages.clean(encode("png", ReportImages.MAX_SIDE + 1, 1))).isInstanceOf(ApiException.class);
    }
}
