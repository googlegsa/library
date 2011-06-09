import java.net.URL;
import java.util.HashMap;
/** A main method that runs a handful of tests. */
class Tests {
  private static void decodeAndEncode(String id) {
    DocId docId = new DocId(id);
    URL url = docId.getFeedFileUrl();
    String decoded = DocId.decode(url);
    if (!id.equals(decoded)) {
      throw new RuntimeException(id + " encode&decode " + decoded);
    }
  }

  private static void decodeAndEncodeDocIds() {
    decodeAndEncode("simple-id");
    decodeAndEncode("harder-id/");
    decodeAndEncode("harder-id/./");
    decodeAndEncode("harder-id///&?///");
    decodeAndEncode("");
    decodeAndEncode(" ");
    decodeAndEncode(" \n\t  ");
    decodeAndEncode("/");
    decodeAndEncode("//");
    decodeAndEncode("drop/table/now");
    decodeAndEncode("/drop/table/now");
    decodeAndEncode("//drop/table/now");
    decodeAndEncode("//d&op/t+b+e/n*w");
  }

  private static void assureHashSame(Object apple, Object orange) {
    if (apple.hashCode() != orange.hashCode()) {
      throw new RuntimeException("" + apple + " hash mismatch with "
          + orange);
    }
  }

  private static void assureEqual(Object apple, Object orange) {
    if (!apple.equals(orange)) {
      throw new RuntimeException("" + apple + " doesn't equal "
          + orange);
    }
  }

  private static void assureDifferent(Object apple, Object orange) {
    if (apple.equals(orange)) {
      throw new RuntimeException("" + apple + " equals "
          + orange);
    }
  }

  private static void hashDocIds() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    DocId id3 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    DocId id4 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    assureHashSame(id,id2);
    assureHashSame(id3,id4);
    HashMap<Object,Object> m = new HashMap<Object,Object>();
    m.put(id, m);
    if (m.get(id2) != m) {
      throw new RuntimeException("key mismatch: " + id2);
    }
    m.put(id3, m);
    if (m.get(id4) != m) {
      throw new RuntimeException("key mismatch: " + id4);
    }
  }

  private static void hashDocReadPermissions() {
    DocReadPermissions perm = new DocReadPermissions("chad", "chap");
    DocReadPermissions perm2 = new DocReadPermissions("chad", "chap");
    DocReadPermissions perm3 = DocReadPermissions.IS_PUBLIC;
    DocReadPermissions perm4 = DocReadPermissions.IS_PUBLIC;
    assureHashSame(perm,perm2);
    assureHashSame(perm3,perm4);
    HashMap<Object,Object> m = new HashMap<Object,Object>();
    m.put(perm, m);
    if (m.get(perm2) != m) {
      throw new RuntimeException("key mismatch: " + perm2);
    }
    m.put(perm3, m);
    if (m.get(perm4) != m) {
      throw new RuntimeException("key mismatch: " + perm4);
    }
  }

  private static void equalDocIds() {
    DocId id = new DocId("procure/book3/sd7823.flx");
    DocId id2 = new DocId("procure/book3/sd7823.flx");
    DocId id3 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    DocId id4 = new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.USE_HEAD_REQUEST);
    assureEqual(id, id2);
    assureEqual(id3, id4);
    assureDifferent(id, id3);
    assureDifferent(id3, new DocId("procure/book3/sd7823.flx",
        DocReadPermissions.IS_PUBLIC));
    assureDifferent(id3, new DocId("procure/book3/XYZXYZ.flx",
        DocReadPermissions.USE_HEAD_REQUEST));
    assureDifferent(id3, new DeletedDocId("procure/book3/sd7823.flx"));
    assureDifferent(new DeletedDocId("procure/book3/sd7823.flx"), id3);
  }

  private static void equalDocReadPermissions() {
    DocReadPermissions perm = new DocReadPermissions("cory", "chap");
    DocReadPermissions perm2 = new DocReadPermissions("cory", "chap");
    DocReadPermissions perm3 = DocReadPermissions.USE_HEAD_REQUEST;
    DocReadPermissions perm4 = DocReadPermissions.USE_HEAD_REQUEST;
    DocReadPermissions perm5 = new DocReadPermissions(null, "chap");
    DocReadPermissions perm6 = new DocReadPermissions("cory", null);
    assureEqual(perm, perm2);
    assureEqual(perm3, perm4);
    assureDifferent(perm, perm3);
    assureDifferent(perm, perm5);
    assureDifferent(perm, perm6);
    assureDifferent(perm3, perm5);
    assureDifferent(perm3, perm6);
  }

  public static void main(String a[]) {
    decodeAndEncodeDocIds();
    equalDocIds();
    equalDocReadPermissions();
    hashDocIds();
    hashDocReadPermissions();
  }
}
