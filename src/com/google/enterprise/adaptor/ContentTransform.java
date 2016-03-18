package com.google.enterprise.adaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The document content transform can modify the content of a document.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class ContentTransform extends OutputStream {

  private static final Logger log =
      Logger.getLogger(ContentTransform.class.getName());
  protected final Map<String, String> config;
  protected final Metadata metadata;
  protected final String contentType;
  private final OutputStream originalStream;

  /**
   * Constructs a document content transform.
   *
   * @param config         the configuration for this instance
   * @param metadata       the unchangeable metadata
   * @param contentType    the input content-type
   * @param originalStream the original stream to put the final content in
   */
  public ContentTransform(final Map<String, String> config,
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
    return "ContentTransform("
        + "config=" + config
        + ", metadata=" + metadata
        + ", contentType='" + contentType + '\''
        + ')';
  }

  /**
   * Specify the transformation in ContentType, where applicable.
   *
   * Because this is a static function, subclasses may *hide* this method by
   * writing another method with the same signature, but make sure not to use
   * the @Override declaration, because Java will flag that as an error.
   *
   * @param ctIn input {@code ContentType} of the Transformation step.
   * @return output {@code ContentType} of the Transformation step.
   */
  public static String getContentTypeOutputForContentTypeInput(String ctIn) {
    // default is just to return the same ContentType as we are passed in.
    log.log(Level.FINEST, "getContentTypeOutputForContentTypeInput({0}) -> {1}",
        new Object[] {ctIn, ctIn});
    return ctIn;
  }
}
