package iq.mizan.helpdesk.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

/**
 * ADR support-1: report images are checked by their bytes, not the declared type, and lose the
 * metadata that can identify a person (EXIF location, camera serials, XMP, text chunks). Nothing
 * is decoded here, so a hostile file costs a byte scan and never a full image in the small heap;
 * the dimension cap protects the operator's browser from decompression bombs instead.
 */
final class ReportImages {

    static final int MAX_SIDE = 8000;

    record Clean(String contentType, byte[] bytes) {
    }

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Set<String> PNG_DROPPED = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private ReportImages() {
    }

    static Clean clean(byte[] bytes) {
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new Clean("image/jpeg", jpeg(bytes));
        }
        if (bytes.length > PNG_SIGNATURE.length && ByteBuffer.wrap(bytes, 0, PNG_SIGNATURE.length).equals(ByteBuffer.wrap(PNG_SIGNATURE))) {
            return new Clean("image/png", png(bytes));
        }
        throw invalid("Attach JPEG or PNG images only");
    }

    private static byte[] jpeg(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(0xD8);
        int i = 2;
        boolean sized = false;
        while (i + 1 < in.length) {
            if ((in[i] & 0xFF) != 0xFF) {
                throw invalid("The image is damaged");
            }
            int marker = in[i + 1] & 0xFF;
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD9) {
                out.write(in, i, 2);
                return requireSized(out.toByteArray(), sized);
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(in, i, 2);
                i += 2;
                continue;
            }
            if (i + 3 >= in.length) {
                throw invalid("The image is damaged");
            }
            int length = ((in[i + 2] & 0xFF) << 8) | (in[i + 3] & 0xFF);
            int end = i + 2 + length;
            if (length < 2 || end > in.length) {
                throw invalid("The image is damaged");
            }
            if (marker == 0xDA) {
                out.write(in, i, in.length - i);
                return requireSized(out.toByteArray(), sized);
            }
            if (isStartOfFrame(marker)) {
                if (length < 7) {
                    throw invalid("The image is damaged");
                }
                int height = ((in[i + 5] & 0xFF) << 8) | (in[i + 6] & 0xFF);
                int width = ((in[i + 7] & 0xFF) << 8) | (in[i + 8] & 0xFF);
                requireSide(width, height);
                sized = true;
            }
            boolean identifying = marker == 0xE1 || marker == 0xED || marker == 0xFE;
            if (!identifying) {
                out.write(in, i, end - i);
            }
            i = end;
        }
        throw invalid("The image is damaged");
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private static byte[] png(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, PNG_SIGNATURE.length);
        int i = PNG_SIGNATURE.length;
        boolean first = true;
        while (i + 12 <= in.length) {
            int length = ByteBuffer.wrap(in, i, 4).getInt();
            if (length < 0 || i + 12L + length > in.length) {
                throw invalid("The image is damaged");
            }
            String type = new String(in, i + 4, 4, StandardCharsets.US_ASCII);
            if (first) {
                if (!"IHDR".equals(type) || length < 8) {
                    throw invalid("The image is damaged");
                }
                requireSide(ByteBuffer.wrap(in, i + 8, 4).getInt(), ByteBuffer.wrap(in, i + 12, 4).getInt());
                first = false;
            }
            int end = i + 12 + length;
            if (!PNG_DROPPED.contains(type)) {
                out.write(in, i, end - i);
            }
            if ("IEND".equals(type)) {
                return out.toByteArray();
            }
            i = end;
        }
        throw invalid("The image is damaged");
    }

    private static byte[] requireSized(byte[] bytes, boolean sized) {
        if (!sized) {
            throw invalid("The image is damaged");
        }
        return bytes;
    }

    private static void requireSide(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) {
            throw invalid("Images can be at most %d pixels on each side".formatted(MAX_SIDE));
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.of(ErrorCode.VALIDATION_FAILED, message);
    }
}
