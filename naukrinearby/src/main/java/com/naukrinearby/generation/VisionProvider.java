package com.naukrinearby.generation;

/** OCR/vision text extraction from a resume image (master plan §2). MVP ships a stub. */
public interface VisionProvider {

	String extractText(byte[] image, String instruction);

	String providerId();
}
