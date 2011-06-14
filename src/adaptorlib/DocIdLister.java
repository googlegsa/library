package adaptorlib;
public interface DocIdLister {
  public void startup();
  public void shutdown();
  public boolean run();
}
