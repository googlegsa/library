import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
/** DocId refers to a unique document in repository.
  You give the GSA a DocId to have it insert your
  document for crawl and index.
  The GSA provides the DocId when it asks your code
  for some information about a particular document in
  repository.  For example when the GSA wants the bytes
  of a particular document or when it wants to find
  out if a particular user has read permissions for it.
  For deleting document from GSA see DeletedDocId
  subclass. */
class DocId {
  private final String uniqId;  // Not null.
  private final DocReadPermissions access;  // Who has access?

  /** Constructs DocId that is marked public (visible by all). */
  DocId(String id) {
    this(id, DocReadPermissions.IS_PUBLIC);
  }

  /** Creates with id and specific permissions. */
  DocId(String id, DocReadPermissions acl) {
    if (null == id) {
      throw new IllegalArgumentException("id cannot be null");
    }
    if (null == acl) {
      throw new IllegalArgumentException("permissions must be provided");
    }
    this.uniqId = id;
    this.access = acl;
  }

  String getUniqueId() {
    return uniqId;
  }
  DocReadPermissions getDocReadPermissions() {
    return access;
  }    

  /** "DocId(" + uniqId + "|" + access + ")" */
  public String toString() {
    return "DocId(" + uniqId + "|" + access + ")";
  }

  private String encode(String s) {
    try {
      String encoding = SystemPreferences.getGsaCharacterEncoding();
      String parts[] = s.split("/", -1);
      StringBuilder encoded = new StringBuilder();
      for (int i = 0; i < parts.length; i++) {
        encoded.append("/");
        encoded.append(URLEncoder.encode(parts[i], encoding));
      }
      return "" + encoded; 
    } catch (java.io.UnsupportedEncodingException uee) {
      throw new IllegalStateException(uee);
    }
  }

  /** Provides URL used in feed file sent to GSA. */
  URL getFeedFileUrl() {
    try {
      if (SystemPreferences.passDocIdToGsaWithoutModification()) {
        return new URL(uniqId);
      } else {
        String prefix = SystemPreferences.getUrlBeginning(this);
        return new URL(prefix + encode(uniqId));
      }
    } catch (MalformedURLException e) {
      throw new IllegalStateException("unable to safely encode " + this);
    }
  }

  /** This default action is "add". */
  String getFeedFileAction() {
    return "add";
  } 

  /** Given a URL that was used in feed file, convert back to doc id. */
  static String decode(URL url) {
    if (SystemPreferences.passDocIdToGsaWithoutModification()) {
      return url.toString();
    } else {
      try {
        String path = url.getPath().substring(1);
        String encoding = SystemPreferences.getGsaCharacterEncoding();
        String parts[] = path.split("/", -1);
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
          decoded.append("/");
          decoded.append(URLDecoder.decode(parts[i], encoding));
        }
        return decoded.substring(1); 
      } catch (java.io.UnsupportedEncodingException uee) {
        throw new IllegalStateException(uee);
      }
    }
  }

  public boolean equals(Object o) {
    boolean same = false;
    if (null != o && getClass().equals(o.getClass())) {
      DocId d = (DocId) o;
      same = this.uniqId.equals(d.uniqId)
          && this.access.equals(d.access);
    }
    return same;
  }

  public int hashCode() {
    return this.uniqId.hashCode();
  }
}
