package com.google.enterprise.adaptor;

import com.google.common.base.Strings;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The content transform factory holds all document content transformers
 * and puts them in series connection.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class ContentTransformFactory {

  private static final Logger LOG =
      Logger.getLogger(ContentTransformFactory.class.getName());

  private List<Map<String, String>> contentTransformers;

  public ContentTransformFactory(
      final List<Map<String, String>> contentTransformers) {
    this.contentTransformers = contentTransformers;
  }

  /**
   * Creates a new transformer pipeline.
   *
   * @param original    original content stream
   * @param contentType content type
   * @param metadata    metadata
   * @return stream pipeline or original output stream
   */
  public final OutputStream createPipeline(final OutputStream original,
                                           final String contentType,
                                           final Metadata metadata) {
    if (contentTransformers.size() <= 0) {
      return original;
    }
    DocumentContentTransform contentTransform = null;
    for (int i = contentTransformers.size(); i >= 0; i--) {
      final Map<String, String> tConfig = contentTransformers.get(i);
      final String className = tConfig.get("class");
      if (Strings.isNullOrEmpty(className)) {
        LOG.log(Level.SEVERE,
            "Document Content Transformer class is missing {0}", className);
        throw new RuntimeException(
            "Document Content Transformer class is missing " + className);
      }
      try {
        //noinspection unchecked
        final Class<DocumentContentTransform> clazz =
            (Class<DocumentContentTransform>) Class.forName(className);
        final Constructor<DocumentContentTransform> constructor =
            clazz.getConstructor(Map.class,
                OutputStream.class, String.class, Metadata.class);
        if (null == contentTransform) {
          contentTransform =
              constructor.newInstance(tConfig, original, contentType,
                  metadata);
        } else {
          contentTransform =
              constructor.newInstance(tConfig, contentTransform,
                  contentType, metadata);
        }
      } catch (Exception e) {
        throw new RuntimeException(
            "Cannot use document content transform of type " + className);
      }
    }
    return contentTransform;
  }
}
