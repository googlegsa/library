// Copyright 2011 Google Inc. All Rights Reserved.
package adaptorlib;
import java.lang.Exception;

/** */
public class TransformException extends Exception {
  public TransformException(Exception e) {
    super(e);
  }
  public TransformException(String s) {
    super(s);
  }
}
