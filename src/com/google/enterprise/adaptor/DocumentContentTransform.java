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
  protected final Metadata metadata;
  protected final String contentType;
  private final OutputStream originalStream;

  /**
   * Constructs a document content transform.
   *
   * @param config         the configuration for this instance
   * @param metadata       the unchangeable metadata
   * @param contentType    the unchangeable content-type
   * @param originalStream the original stream to put the final content in
   */
  public DocumentContentTransform(final Map<String, String> config,
                                  final Metadata metadata,
                                  final String contentType,
                                  final OutputStream originalStream) {
    if (null == originalStream) {
      throw new NullPointerException("the original stream must not be null");
    }
    this.config = Collections.unmodifiableMap(
        new TreeMap<String, String>(config));
    this.metadata = metadata;
    this.contentType = contentType;
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
    return "DocumentContentTransform{"
        + "config=" + config
        + ", metadata=" + metadata
        + ", contentType='" + contentType + '\''
        + '}';
  }
}
