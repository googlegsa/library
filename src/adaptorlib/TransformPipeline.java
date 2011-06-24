// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.Exception;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/** */
public class TransformPipeline extends AbstractList<DocumentTransform> {

  /**
   * ContentIn and metadataIn are guaranteed to remain unchanged.
   */
  public void transform(ByteArrayOutputStream contentIn,
                        ByteArrayOutputStream metadataIn,
                        ByteArrayOutputStream contentOut,
                        ByteArrayOutputStream metadataOut,
                        Map<String, String> params) throws TransformException, IOException {
    if (transformList.isEmpty()) {
      contentIn.writeTo(contentOut);
      metadataIn.writeTo(metadataOut);
      return;
    }

    ByteArrayOutputStream contentInTransit = new ByteArrayOutputStream();
    ByteArrayOutputStream metaInTransit = new ByteArrayOutputStream();
    contentIn.writeTo(contentInTransit);
    metadataIn.writeTo(metaInTransit);
    for (int i = 0; i < transformList.size(); i++) {
      DocumentTransform transform = transformList.get(i);
      try {
        transform.transform(contentInTransit, metaInTransit, contentOut, metadataOut, params);
      }
      catch (TransformException e) {
        // TODO: Log error
        if (transform.errorHaltsPipeline()) {
          contentOut.reset();
          metadataOut.reset();
          throw e;
        }
        else {
          continue;
        }
      }
      if (i < transformList.size() - 1) {
        contentOut.writeTo(contentInTransit);
        metadataOut.writeTo(metaInTransit);
      }
    }
  }

  @Override
  public void add(int index, DocumentTransform transform) {
    transformList.add(index, transform);
  }

  @Override
  public DocumentTransform get(int index) {
    return transformList.get(index);
  }

  @Override
  public DocumentTransform set(int index, DocumentTransform transform) {
    return transformList.set(index, transform);
  }

  @Override
  public DocumentTransform remove(int index) {
    return transformList.remove(index);
  }

  @Override
  public int size() {
    return transformList.size();
  }

  private ArrayList<DocumentTransform> transformList = new ArrayList<DocumentTransform>();
}
