package com.google.enterprise.adaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * The document content transformer can modify the content of a document.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class DocumentContentTransformer extends OutputStream {

  /**
   * Configuration.
   */
  private final Map<String, String> config;

  /**
   * Original content stream.
   */
  private OutputStream originalStream;

  /**
   * Content type.
   */
  private String contentType;

  /**
   * Metadata.
   */
  private final Metadata metadata;

  /**
   * Constructor.
   *
   * @param config         {@link #config}
   * @param originalStream {@link #originalStream}
   * @param contentType    {@link #contentType}
   * @param metadata       {@link #metadata}
   */
  public DocumentContentTransformer(final Map<String, String> config,
                                    final OutputStream originalStream, final String contentType,
                                    final Metadata metadata) {
    this.config = config;
    this.originalStream = originalStream;
    this.contentType = contentType;
    this.metadata = metadata;
  }

  /**
   * Getter for {@link #config}.
   *
   * @return {@link #config}
   */
  public final Map<String,String> config() {
    return config;
  }

  /**
   * Getter for {@link #originalStream}.
   *
   * @return {@link #originalStream}
   */
  public final OutputStream originalStream() {
    return originalStream;
  }

  /**
   * Getter for {@link #contentType}.
   *
   * @return {@link #contentType}
   */
  public final String contentType() {
    return contentType;
  }

  /**
   * Getter for {@link #metadata}.
   *
   * @return {@link #metadata}
   */
  public final Metadata metadata() {
      return metadata;
  }

  @Override
  public void write(int b) throws IOException {
    originalStream().write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    originalStream().write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    originalStream().write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    originalStream.flush();
  }

  @Override
  public void close() throws IOException {
    originalStream.close();
  }
}
