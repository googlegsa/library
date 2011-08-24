package adaptorlib;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/** Keeps set of MetaItem instanes after validation. */
public final class Metadata implements Iterable<MetaItem> {
  private Set<MetaItem> items;
 
  /**
   * Validates that each meta name is unique, there is either
   * public-indicator or ACLs and that ACLs values are acceptable.
   */ 
  public Metadata(Set<MetaItem> allMeta) {
    items = Collections.unmodifiableSet(new TreeSet<MetaItem>(allMeta));
    checkConsistency(items);
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
    return Arrays.hashCode(items.toArray());
  }

  public Iterator<MetaItem> iterator() {
    return items.iterator();
  }

  public String toString() {
    return items.toString();
  }

  private static void checkConsistency(Set<MetaItem> allMeta) {
    checkEachNameIsUnique(allMeta); 
    checkXorPublicAndAcls(allMeta);
    checkBothOrNoneAcls(allMeta); 
    checkPublicIsBoolean(allMeta); 
  }

  /** Each MetaItem name needs be unique. */
  private static void checkEachNameIsUnique(Set<MetaItem> m) {
    HashSet<String> unique = new HashSet<String>();
    HashSet<String> dup = new HashSet<String>();
    for (MetaItem item : m) {
      if (unique.contains(item.getName())) {
        dup.add(item.getName());
      } else {
        unique.add(item.getName());
      }
    }
    if (0 < dup.size()) {
      throw new IllegalArgumentException("duplicate names: " + dup);
    }
  }

  /** Either have public indicator or ACLs, but not both, nor neither. */
  private static void checkXorPublicAndAcls(Set<MetaItem> m) {
    boolean hasPublicName = containsName(m, "google:ispublic");
    boolean hasAcls = containsName(m, "google:aclusers")
        || containsName(m, "google:aclgroups");
    if (hasPublicName && hasAcls) {
      throw new IllegalArgumentException("has both ispublic and ACLs");
    } else if (!hasPublicName && !hasAcls) {
      throw new IllegalArgumentException("has neither ispublic nor ACLs");
    }
  }

  /** Cannot provide users without groups and vice-versa. */
  private static void checkBothOrNoneAcls(Set<MetaItem> m) {
    boolean hasUserAcls = containsName(m, "google:aclusers");
    boolean hasGroupAcls = containsName(m, "google:aclgroups");
    if (hasUserAcls && !hasGroupAcls) {
      throw new IllegalArgumentException("has users, but not groups");
    } else if (hasGroupAcls && !hasUserAcls) {
      throw new IllegalArgumentException("has groups, but not users");
    } else if (hasGroupAcls && hasUserAcls) {
      String userLine = getValue(m, "google:aclusers").trim();
      String groupLine = getValue(m, "google:aclgroups").trim();
      if (userLine.isEmpty() && groupLine.isEmpty()) {
        throw new IllegalArgumentException("both users and groups empty");
      }
    }
  }

  /** If has public indicator value is acceptable. */
  private static void checkPublicIsBoolean(Set<MetaItem> m) {
    boolean hasPublicName = containsName(m, "google:ispublic");
    if (hasPublicName) {
      String value = getValue(m, "google:ispublic");
      if (!"true".equals(value) && !"false".equals(value)) {
        throw new IllegalArgumentException("ispublic is not true nor false");
      }
    }
  }

  private static String getValue(Set<MetaItem> m, String name) {
    for (MetaItem item : m) {
      if (item.getName().equals(name)) {
        return item.getValue();
      }
    }
    return null;
  }

  private static boolean containsName(Set<MetaItem> m, String name) {
    return null != getValue(m, name);
  }
}
