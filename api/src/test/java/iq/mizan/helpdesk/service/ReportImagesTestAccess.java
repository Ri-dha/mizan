package iq.mizan.helpdesk.service;

import java.io.IOException;

/** Lets tests in other packages build the sample images. */
public final class ReportImagesTestAccess {

    private ReportImagesTestAccess() {
    }

    public static byte[] jpegWithLocation() throws IOException {
        return ReportImagesTest.jpegWithLocation();
    }

    public static byte[] pngWithText() throws IOException {
        return ReportImagesTest.pngWithText();
    }
}
