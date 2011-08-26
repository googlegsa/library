package adaptorlib;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Keeps set of MetaItem instanes after validation. */
public final class Metadata implements Iterable<MetaItem> {
  private final Set<MetaItem> items;
 
  /**
   * Validates that each meta name is unique, there is either
   * public-indicator or ACLs and that ACLs values are acceptable.
   */ 
  public Metadata(Set<MetaItem> allMeta) {
    items = Collections.unmodifiableSet(new TreeSet<MetaItem>(allMeta));
    checkConsistency(toMap());
  }

  public boolean equals(Object o) {
    boolean same = false;
    if (null != o && this.getClass().equals(o.getClass())) {
      Metadata other = (Metadata) o;
      same = items.equals(other.items);
    } 
    return same;
  }

  public int hashCode() {
    return items.hashCode();
  }

  public Iterator<MetaItem> iterator() {
    return items.iterator();
  }

  public String toString() {
    return items.toString();
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<String, String>();
    for (MetaItem item : this) {
      map.put(item.getName(), item.getValue());
    }
    return map;
  }

  private static void checkConsistency(Map<String, String> allMeta) {
    checkEachNameIsUnique(allMeta); 
    checkXorPublicAndAcls(allMeta);
    checkBothOrNoneAcls(allMeta); 
    checkPublicIsBoolean(allMeta); 
  }

  /** Each MetaItem name needs be unique. */
  private static void checkEachNameIsUnique(Map<String, String> m) {
    HashSet<String> unique = new HashSet<String>();
    HashSet<String> dup = new HashSet<String>();
    for (String name : m.keySet()) {
      if (unique.contains(name)) {
        dup.add(name);
      } else {
        unique.add(name);
      }
    }
    if (0 < dup.size()) {
      throw new IllegalArgumentException("duplicate names: " + dup);
    }
  }

  /** Either have public indicator or ACLs, but not both, nor neither. */
  private static void checkXorPublicAndAcls(Map<String, String> m) {
    boolean hasPublicName = m.containsKey("google:ispublic");
    boolean hasAcls = m.containsKey("google:aclusers")
        || m.containsKey("google:aclgroups");
    if (hasPublicName && hasAcls) {
      throw new IllegalArgumentException("has both ispublic and ACLs");
    } else if (!hasPublicName && !hasAcls) {
      throw new IllegalArgumentException("has neither ispublic nor ACLs");
    }
  }

  /** Cannot provide users without groups and vice-versa. */
  private static void checkBothOrNoneAcls(Map<String, String> m) {
    boolean hasUserAcls = m.containsKey("google:aclusers");
    boolean hasGroupAcls = m.containsKey("google:aclgroups");
    if (hasUserAcls && !hasGroupAcls) {
      throw new IllegalArgumentException("has users, but not groups");
    } else if (hasGroupAcls && !hasUserAcls) {
      throw new IllegalArgumentException("has groups, but not users");
    } else if (hasGroupAcls && hasUserAcls) {
      String userLine = m.get("google:aclusers").trim();
      String groupLine = m.get("google:aclgroups").trim();
      if (userLine.isEmpty() && groupLine.isEmpty()) {
        throw new IllegalArgumentException("both users and groups empty");
      }
    }
  }

  /** If has public indicator value is acceptable. */
  private static void checkPublicIsBoolean(Map<String, String> m) {
    String value = m.get("google:ispublic");
    if (null != value) {
      if (!"true".equals(value) && !"false".equals(value)) {
        throw new IllegalArgumentException("ispublic is not true nor false");
      }
    }
  }
}
