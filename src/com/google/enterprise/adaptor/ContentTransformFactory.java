package com.google.enterprise.adaptor;

import com.google.common.base.Strings;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The content transform factory holds all document content transforms
 * and puts them in series connection.
 *
 * @author Dominik Weidenfeld (dominik.weidenfeld@twt.de)
 */
public class ContentTransformFactory {

  private Map<Constructor<DocumentContentTransform>, Map<String, String>>
      transforms;

  /**
   * Constructs a new ContentTransformFactory. Also checks the basic
   * configuration values for the {@link DocumentContentTransform}
   * instantiation.
   *
   * @param configs Configuration for each {@link DocumentContentTransform}
   * @throws InvalidConfigurationException If the class for a {@link
   *                                       DocumentContentTransform} is missing
   * @throws RuntimeException              If the class does not match all
   *                                       criteria
   */
  public ContentTransformFactory(
      final List<Map<String, String>> configs) {
    if (configs.size() <= 0) {
      return;
    }
    this.transforms = new LinkedHashMap<Constructor<DocumentContentTransform>,
        Map<String, String>>();
    for (int i = (configs.size() - 1); i >= 0; i--) {
      final Map<String, String> config = configs.get(i);
      final String className = config.get("class");
      if (Strings.isNullOrEmpty(className)) {
        throw new InvalidConfigurationException(
            "Document Content Transform class is missing: " + className);
      }
      try {
        @SuppressWarnings("unchecked")
        final Class<DocumentContentTransform> clazz =
            (Class<DocumentContentTransform>) Class.forName(className);
        final Constructor<DocumentContentTransform> constructor =
            clazz.getConstructor(Map.class, Metadata.class,
                String.class, OutputStream.class);
        this.transforms.put(constructor, new TreeMap<String, String>(config));
      } catch (Exception e) {
        throw new InvalidConfigurationException(
            "Cannot get document content transform of type: " + className, e);
      }
    }
  }

  /**
   * Creates a new transform pipeline.
   *
   * @param original    original content stream
   * @param contentType content type
   * @param metadata    metadata
   * @return stream pipeline or original output stream
   */
  public final OutputStream createPipeline(final OutputStream original,
                                           final String contentType,
                                           final Metadata metadata) {
    if (null == transforms || transforms.size() <= 0) {
      return original;
    }
    DocumentContentTransform last = null;
    for (Map.Entry<Constructor<DocumentContentTransform>, Map<String, String>>
        t : transforms.entrySet()) {
      try {
        if (null == last) {
          last = t.getKey()
              .newInstance(t.getValue(), metadata, contentType, original);
        } else {
          last = t.getKey()
              .newInstance(t.getValue(), metadata, contentType, last);
        }
      } catch (Exception e) {
        throw new RuntimeException(
            "Cannot instantiate document content transform: "
                + t.getKey().getName(), e);
      }
    }
    return last;
  }
}
