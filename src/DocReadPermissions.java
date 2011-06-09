import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
/** Identifies who has ability to read a document.  
  <ul><li> Is document public?
      <li> Or should head requests be used to figure out access? 
      <li> Or is it accessible by named users and named groups?
  <p>
  Named users and groups are sent to GSA inside
  a metadata tag like described in
http://code.google.com/apis/searchappliance/documentation/64/feedsguide.html .
The summary is that GSA takes comma seperate values like
"alice, bob" and "eng, marketing, admin".
  <p>
A head request is a request from GSA with a user name
and document id that happens just before search results
are returned and filters results out for lack of permission.  */
final class DocReadPermissions {

  /** Use to mark a document as visible by all. */
  static final DocReadPermissions IS_PUBLIC
      = new DocReadPermissions(null, null, true);

  /** Use to have GSA request authorization at time of search. */
  static final DocReadPermissions USE_HEAD_REQUEST
      = new DocReadPermissions(null, null, false);

  private final String users;
  private final String groups;
  private final boolean isPublic;

  /** Makes a non-public DocReadPermissions that specifies 
    users and groups who can read a document.
    Cannot both be empty. Null is empty. All blank is empty. */
  DocReadPermissions(String users, String groups) {
    if (null != users && users.trim().isEmpty()) {
      users = null;
    }
    if (null != groups && groups.trim().isEmpty()) {
      groups = null;
    }
    boolean emptyUsers = null == users;
    boolean emptyGroups = null == groups;
    if (emptyUsers && emptyGroups) {
      throw new IllegalArgumentException("users and groups are empty");
    }
    this.users = users;
    this.groups = groups;
    isPublic = false;
  }
  
  /** Constructor for constants IS_PUBLIC and USE_HEAD_REQUEST. */
  private DocReadPermissions(String users, String groups,
      boolean isPublic) {
    this.users = users;
    this.groups = groups;
    this.isPublic = isPublic;
  }
  
  /** Returns user names String or null. */
  String getUsers() {
    return users;
  }
  /** Returns group names String or null. */
  String getGroups() {
    return groups;
  }
  /** Says whether this document is visible by all. */
  boolean isPublic() {
    return isPublic;
  }

  /** "DocReadPermissions(" + users + "|" + groups + "|" + isPublic + ")" */
  public String toString() {
    return "DocReadPermissions(" + users + "|" + groups + "|" + isPublic + ")";
  }

  private boolean stringsEqual(String a, String b) {
    if (null == a && null == b) {
      return true;
    } else if (null == a ^ null == b) {
      return false;
    } else {
      return a.equals(b);
    }
  }

  public boolean equals(Object o) {
    boolean same = false;
    if (o instanceof DocReadPermissions) {
      DocReadPermissions that = (DocReadPermissions) o;
      same = stringsEqual(this.users, that.users)
          && stringsEqual(this.groups, that.groups)
          && (this.isPublic == that.isPublic);
    }
    return same;
  }

  public int hashCode() {
    return Arrays.hashCode(new Object[]{users, groups, isPublic});
  }
}
