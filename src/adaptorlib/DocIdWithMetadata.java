package adaptorlib;
import java.util.Arrays;

public class DocIdWithMetadata extends DocId {
  private Metadata metabox;

  public DocIdWithMetadata(String id, Metadata metadata) {
    super(id);
    if (null == metadata) {
      throw new IllegalArgumentException("metadata is required");
    }
    this.metabox = metadata;
  }

  Metadata getMetadata() {
    return metabox;
  }

  /** "DocIdWithMetadata(" + getUniqueId() + "," + metadata + ")" */
  public String toString() {
    return "DocIdWithMetadata(" + getUniqueId() + "," + metabox + ")";
  }

  public boolean equals(Object o) {
    boolean same = false;
    if (null != o && getClass().equals(o.getClass())) {
      DocIdWithMetadata d = (DocIdWithMetadata) o;
      same = super.equals(d) && metabox.equals(d.metabox);
    }
    return same;
  }

  public int hashCode() {
    Object parts[] = new Object[] { super.hashCode(), metabox.hashCode() };
    return Arrays.hashCode(parts);
  }
}
