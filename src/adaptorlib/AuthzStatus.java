package adaptorlib;

/**
 * Authorization Status codes.
 * <ul>
 * <li>{@code PERMIT} means that authorization is granted.</li>
 * <li>{@code DENY} means that authorization is explicitly denied.</li>
 * <li>{@code INDETERMINATE} means that permission is neither granted nor
 * denied. If a consumer receives this code, it may decide to try other means
 * to get an explicit decision (i.e. {@code PERMIT} or {@code DENY}).</li>
 * </ul>
 */
public enum AuthzStatus {
  PERMIT("Access PERMITTED"),
  DENY("Access DENIED"),
  INDETERMINATE("No access decision");

  private final String description;

  private AuthzStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
