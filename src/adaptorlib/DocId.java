package adaptorlib;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
/** DocId refers to a unique document in repository.
  You give the adaptorlib a DocId to have it insert your
  document for crawl and index.
  The adaptorlib provides the DocId when it asks your code
  for some information about a particular document in
  repository.  For example when the adaptorlib wants the bytes
  of a particular document or when it wants to find
  out if a particular user has read permissions for it.
  For deleting document from adaptorlib see DeletedDocId
  subclass. */
public class DocId {
  private final String uniqId;  // Not null.
  private final DocReadPermissions access;  // Who has access?

  /** Constructs DocId that is marked public (visible by all). */
  public DocId(String id) {
    this(id, DocReadPermissions.IS_PUBLIC);
  }

  /** Creates with id and specific permissions. Arugments may not be null. */
  public DocId(String id, DocReadPermissions acl) {
    if (id == null || acl == null) {
      throw new NullPointerException();
    }
    this.uniqId = id;
    this.access = acl;
  }

  public String getUniqueId() {
    return uniqId;
  }

  public DocReadPermissions getDocReadPermissions() {
    return access;
  }

  /** "DocId(" + uniqId + "|" + access + ")" */
  public String toString() {
    return "DocId(" + uniqId + "|" + access + ")";
  }

  /** This default action is "add". */
  String getFeedFileAction() {
    return "add";
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

// Implement in terms of generic DocId builder. 
/** A DeletedDocId is a DocId that when sent to GSA
  results in quickly removing referenced document
  from crawling and index.
  <p> Please note that GSA will figure out a document
  is deleted on its own and sending a DeletedDocId is
  optional.  Sending the GSA DeletedDocId
  instances will be faster than waiting for GSA to
  realize a document has been deleted.
  <p> Look at DocId for more details. */
//class DeletedDocId extends DocId {
//  DeletedDocId(String id) {
//    super(id, DocReadPermissions.USE_HEAD_REQUEST);
//  }
//
//  /** Provides delete for action attribute value. */
//  String getFeedFileAction() {
//    return "delete";
//  } 
//
//  /** "DeletedDocId(" + getUniqueId() + ")" */
//  public String toString() {
//    return "DeletedDocId(" + getUniqueId() + ")";
//  }
//}
