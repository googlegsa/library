package com.google.enterprise.adaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The document content transform can modify the content of a document.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class DocumentContentTransform extends OutputStream {

  protected final Map<String, String> config;
  protected Metadata metadata;
  protected String contentType;
  private OutputStream originalStream;

  /**
   * Constructs a document content transform.
   *
   * @param config the configuration for this instance
   */
  public DocumentContentTransform(final Map<String, String> config) {
    this.config = Collections.unmodifiableMap(
        new TreeMap<String, String>(config));
  }

  public final void setMetadata(final Metadata metadata) {
    this.metadata = metadata;
  }

  public final void setContentType(final String contentType) {
    this.contentType = contentType;
  }

  public final void setOriginalStream(final OutputStream originalStream) {
    if (null == originalStream) {
      throw new NullPointerException();
    }
    this.originalStream = originalStream;
  }

  @Override
  public void write(int b) throws IOException {
    originalStream.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    originalStream.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    originalStream.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    originalStream.flush();
  }

  @Override
  public void close() throws IOException {
    originalStream.close();
  }

  @Override
  public final String toString() {
    return "DocumentContentTransform{" +
        "config=" + config +
        ", metadata=" + metadata +
        ", contentType='" + contentType + '\'' +
        '}';
  }
}
