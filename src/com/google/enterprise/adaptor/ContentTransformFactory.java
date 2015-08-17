package com.google.enterprise.adaptor;

import com.google.common.base.Strings;

import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
   * @param transforms Configuration for each {@link DocumentContentTransform}
   * @throws InvalidConfigurationException If the class for a {@link
   *                                       DocumentContentTransform} is missing
   * @throws RuntimeException              If the class does not match all
   *                                       criteria
   */
  public ContentTransformFactory(
      final List<Map<String, String>> transforms) {
    if (transforms.size() <= 0) {
      return;
    }
    this.transforms = new LinkedHashMap<Constructor<DocumentContentTransform>,
        Map<String, String>>();
    for (int i = (transforms.size() - 1); i >= 0; i--) {
      final Map<String, String> tConfig = transforms.get(i);
      final String className = tConfig.get("class");
      if (Strings.isNullOrEmpty(className)) {
        throw new InvalidConfigurationException(
            "Document Content Transform class is missing: " + className);
      }
      try {
        //noinspection unchecked
        final Class<DocumentContentTransform> clazz =
            (Class<DocumentContentTransform>) Class.forName(className);
        final Constructor<DocumentContentTransform> constructor =
            clazz.getConstructor(Map.class);
        this.transforms.put(constructor, tConfig);
      } catch (Exception e) {
        throw new RuntimeException(
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
        final DocumentContentTransform transform =
            t.getKey().newInstance(t.getValue());
        if (null == last) {
          transform.setOriginalStream(original);
        } else {
          transform.setOriginalStream(last);
        }
        transform.setContentType(contentType);
        transform.setMetadata(metadata);
        last = transform;
      } catch (Exception e) {
        throw new RuntimeException(
            "Cannot instantiate document content transform: "
                + t.getKey().getName(), e);
      }
    }
    return last;
  }
}
