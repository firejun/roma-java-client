package jp.co.rakuten.rit.roma.client;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.rakuten.rit.roma.client.commands.Command;
import jp.co.rakuten.rit.roma.client.commands.CommandContext;
import jp.co.rakuten.rit.roma.client.commands.CommandID;
import jp.co.rakuten.rit.roma.client.commands.GetsOptCommand;

/**
 * ROMA client to ROMA, which is a cluster of ROMA instances. The basic usage is
 * written on {@link jp.co.rakuten.rit.roma.client.RomaClientFactory}.
 * 
 * @version 0.4.1
 */
public class RomaClientImpl extends AbstractRomaClient {
  private static Logger LOG = LoggerFactory.getLogger(RomaClientImpl.class);

  protected boolean opened = false;

  public RomaClientImpl() {
  }

  /**
   * @see RomaClient#isOpen()
   */
  public synchronized boolean isOpen() {
    return opened;
  }

  /**
   * @see RomaClient#open(Node)
   */
  public synchronized void open(final Node node) throws ClientException {
    List<Node> initNodes = new ArrayList<Node>();
    initNodes.add(node);
    open(initNodes);
  }

  static class ShutdownHookThread extends Thread {

    public void run() {
      // System.out.println("Call Shutdown hook");
      GetsOptCommand.shutdown();
      //TimeoutFilter.shutdown();
    }
  }

  /**
   * @see RomaClient#open(List)
   */
  public synchronized void open(final List<Node> nodes) throws ClientException {
    if (nodes == null) {
      throw new IllegalArgumentException();
    }
    if (nodes.size() == 0) {
      throw new IllegalArgumentException();
    }

    // connect to ROMA and get the routing info via a network
    routingdump(nodes);
    if (!routingTable.enableLoop()) {
      routingTable.startLoop();
      opened = true;
    }
    Runtime r = Runtime.getRuntime();
    r.addShutdownHook(new ShutdownHookThread());
  }

  /**
   * @see RomaClient#close()
   */
  public synchronized void close() throws ClientException {
    if (!opened) {
      return;
    }
    routingTable.stopLoop();
    routingTable.clear();
    routingTable = null;
    //TimeoutFilter.shutdown();
    opened = false;
  }

  public void routingdump(List<Node> nodes) throws ClientException {
    if (nodes == null) {
      throw new IllegalArgumentException();
    }
    if (nodes.size() == 0) {
      throw new IllegalArgumentException();
    }

    Iterator<Node> iter = nodes.iterator();
    List<Object> list = null;
    List<String> err = new ArrayList<String>();
    Throwable t = null;
    while (iter.hasNext()) {
      Node node = null;
      try {
        node = iter.next();
        list = routingdump(node);
        if (list != null) {
          routingTable.init(list);
          t = null;
          break;
        }
      } catch (ClientException e) {
        err.add(e.getMessage());
        err.add(node.toString());
        t = e.getCause();
      }
    }
    if (t != null) {
      throw new ClientException(err.toString(), t);
    }
    if (list == null) {
      throw new ClientException("fatal error");
    }
  }

  /**
   * @see RomaClient#routingdump(Node)
   */
  @SuppressWarnings("unchecked")
  public List<Object> routingdump(Node node) throws ClientException {
    if (node == null) {
      throw new NullPointerException();
    }

    try {
      CommandContext context = new CommandContext();
      context.put(CommandContext.NODE, node);
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.COMMAND_ID, CommandID.ROUTING_DUMP);
      Command command = commandFact.getCommand(CommandID.ROUTING_DUMP);
      boolean ret = command.execute(context);
      if (ret) {
        return (List<Object>) context.get(CommandContext.RESULT);
      } else {
        return null;
      }
    } catch (ClientException e) {
      LOG.error("routingdump failed: " + node, e);
      throw e;
    }
  }

  /**
   * @see RomaClient#routingmht(Node)
   */
  public String routingmht(Node node) throws ClientException {
    if (node == null) {
      throw new NullPointerException();
    }

    try {
      CommandContext context = new CommandContext();
      context.put(CommandContext.NODE, node);
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.COMMAND_ID, CommandID.ROUTING_MKLHASH);
      Command command = commandFact.getCommand(CommandID.ROUTING_MKLHASH);
      boolean ret = exec(command, context);
      if (ret) {
        String s = (String) context.get(CommandContext.RESULT);
        if (s.equals("")) {
          return null;
        } else {
          return s;
        }
      } else {
        return null;
      }
    } catch (ClientException e) {
      LOG.error("routingmht failed: " + node, e);
      throw e;
    }
  }

  /**
   * @see RomaClient#nodelist()
   */
  public List<String> nodelist() throws ClientException {
    List<Node> nodes = routingTable.getPhysicalNodes();
    if (nodes == null) {
      throw new BadRoutingTableFormatException("nodes is null.");
    }
    List<String> ret = new ArrayList<String>();
    for (Iterator<Node> iter = nodes.iterator(); iter.hasNext();) {
      ret.add(iter.next().toString());
    }
    return ret;
  }

  public boolean expire(String key, long expiry) throws ClientException {
    return update0(CommandID.EXPIRE, key, new byte[0], expiry);
  }

  /**
   * @see RomaClient#put(String, byte[])
   */
  public boolean put(String key, byte[] value) throws ClientException {
    return put(key, value, 0);
  }

  /**
   * @see RomaClient#put(String, byte[], Date)
   */
  @Deprecated
  public boolean put(String key, byte[] value, Date expiry)
      throws ClientException {
    return update(CommandID.SET, key, value, expiry);
  }

  /**
   * @see RomaClient#put(String, byte[], Date)
   */
  public boolean put(String key, byte[] value, long expiry)
      throws ClientException {
    return update0(CommandID.SET, key, value, expiry);
  }

  /**
   * @see RomaClient#append(String, byte[])
   */
  public boolean append(String key, byte[] value) throws ClientException {
    return append(key, value, 0);
  }

  /**
   * @see RomaClient#append(String, byte[], Date)
   */
  @Deprecated
  public boolean append(String key, byte[] value, Date expiry)
      throws ClientException {
    return update(CommandID.APPEND, key, value, expiry);
  }

  public boolean append(String key, byte[] value, long expiry)
      throws ClientException {
    return update0(CommandID.APPEND, key, value, expiry);
  }

  /**
   * @see RomaClient#prepend(String, byte[])
   */
  public boolean prepend(String key, byte[] value) throws ClientException {
    return prepend(key, value, 0);
  }

  /**
   * @see RomaClient#prepend(String, byte[], Date)
   */
  @Deprecated
  public boolean prepend(String key, byte[] value, Date expiry)
      throws ClientException {
    return update(CommandID.PREPEND, key, value, expiry);
  }

  public boolean prepend(String key, byte[] value, long expiry)
      throws ClientException {
    return update0(CommandID.PREPEND, key, value, expiry);
  }

  protected boolean update(int commandID, String key, byte[] value, Date expiry)
      throws ClientException {
    CommandContext context = new CommandContext();
    try {
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.KEY, key);
      context.put(CommandContext.HASH_NAME, hashName);
      context.put(CommandContext.VALUE, value);
      context.put(CommandContext.EXPIRY, expiry);
      context.put(CommandContext.COMMAND_ID, commandID);
      Command command = commandFact.getCommand(commandID);
      return exec(command, context);
    } catch (ClientException e) {
      LOG.error("update failed: " + key, e);
      throw e;
    }
  }

  protected boolean update0(final int commandID, final String key,
      final byte[] value, final long expiry) throws ClientException {
    CommandContext context = new CommandContext();
    try {
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.KEY, key);
      context.put(CommandContext.HASH_NAME, hashName);
      context.put(CommandContext.VALUE, value);
      context.put(CommandContext.EXPIRY, "" + expiry);
      context.put(CommandContext.COMMAND_ID, commandID);
      Command command = commandFact.getCommand(commandID);
      return exec(command, context);
    } catch (ClientException e) {
      LOG.error("update failed: " + key, e);
      throw e;
    }
  }

  /**
   * @see RomaClient#get(String)
   */
  public byte[] get(String key) throws ClientException {
    List<String> keys = new ArrayList<String>();
    keys.add(key);
    return (byte[]) gets(CommandID.GET, keys);
  }

  public Map<String, byte[]> gets(List<String> keys) throws ClientException {
    return gets(keys, false);
  }

  @SuppressWarnings("unchecked")
  public Map<String, byte[]> gets(List<String> keys, boolean useThreads)
      throws ClientException {
    if (useThreads) {
      return (Map<String, byte[]>) gets(CommandID.GETS_OPT, keys);
    } else {
      return (Map<String, byte[]>) gets(CommandID.GETS, keys);
    }
  }

  public Map<String, CasValue> getsWithCasID(List<String> keys)
      throws ClientException {
    return getsWithCasID(keys, false);
  }

  @SuppressWarnings("unchecked")
  public Map<String, CasValue> getsWithCasID(List<String> keys,
      boolean useThreads) throws ClientException {
    if (useThreads) {
      return (Map<String, CasValue>) gets(CommandID.GETS_WITH_CASID_OPT, keys);
    } else {
      return (Map<String, CasValue>) gets(CommandID.GETS_WITH_CASID, keys);
    }
  }

  protected Object gets(int commandID, List<String> keys)
      throws ClientException {
    if (keys.size() == 0) {
      return new HashMap<String, byte[]>();
    }

    CommandContext context = new CommandContext();
    try {
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.KEY, keys.get(0));
      context.put(CommandContext.KEYS, keys);
      context.put(CommandContext.HASH_NAME, hashName);
      context.put(CommandContext.COMMAND_ID, commandID);
      Command command = commandFact.getCommand(commandID);
      boolean ret = exec(command, context);
      if (ret) {
        return context.get(CommandContext.RESULT);
      } else {
        return null;
      }
    } catch (ClientException e) {
      LOG.error("gets failed: " + keys, e);
      throw e;
    }
  }

  /**
   * @see RomaClient#delete(String)
   */
  public boolean delete(String key) throws ClientException {
    return delete(CommandID.DELETE, key);
  }

  protected boolean delete(int commandID, String key) throws ClientException {
    CommandContext context = new CommandContext();
    try {
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.KEY, key);
      context.put(CommandContext.HASH_NAME, hashName);
      context.put(CommandContext.COMMAND_ID, commandID);
      Command command = commandFact.getCommand(commandID);
      return exec(command, context);
    } catch (ClientException e) {
      LOG.error("delete failed: " + key, e);
      throw e;
    }
  }

  /**
   * @see RomaClient#incr(String, int)
   */
  public BigInteger incr(String key, int count) throws ClientException {
    return incrAndDecr(CommandID.INCREMENT, key, count);
  }

  /**
   * @see RomaClient#decr(String, int)
   */
  public BigInteger decr(String key, int count) throws ClientException {
    return incrAndDecr(CommandID.DECREMENT, key, count);
  }

  protected BigInteger incrAndDecr(int commandID, String key, int count)
      throws ClientException {
    CommandContext context = new CommandContext();
    try {
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.KEY, key);
      context.put(CommandContext.HASH_NAME, hashName);
      context.put(CommandContext.VALUE, new Integer(count));
      context.put(CommandContext.COMMAND_ID, commandID);
      Command command = commandFact.getCommand(commandID);
      boolean ret = exec(command, context);
      if (ret) {
        return (BigInteger) context.get(CommandContext.RESULT);
      } else {
        return new BigInteger("-1");
      }
    } catch (ClientException e) {
      LOG.error("incr or decr failed: " + key, e);
      throw e;
    }
  }

  /**
   * @see RomaClient#add(String, byte[])
   */
  public boolean add(String key, byte[] value) throws ClientException {
    return add(key, value, 0);
  }

  /**
   * @see RomaClient#add(String, byte[], Date)
   */
  @Deprecated
  public boolean add(String key, byte[] value, Date expiry)
      throws ClientException {
    return update(CommandID.ADD, key, value, expiry);
  }

  /**
   * @see RomaClient#add(String, byte[], Date)
   */
  public boolean add(String key, byte[] value, long expiry)
      throws ClientException {
    return update0(CommandID.ADD, key, value, expiry);
  }

  public CasResponse cas(String key, long casID, byte[] value)
      throws ClientException {
    return cas(key, casID, value, new Date(0));
  }

  @Deprecated
  public CasResponse cas(String key, long casID, byte[] value, Date expiry)
      throws ClientException {
    int commandID = CommandID.CAS;
    CommandContext context = new CommandContext();
    try {
      context.put(CommandContext.CONNECTION_POOL, connPool);
      context.put(CommandContext.ROUTING_TABLE, routingTable);
      context.put(CommandContext.KEY, key);
      context.put(CommandContext.HASH_NAME, hashName);
      context.put(CommandContext.CAS_ID, casID);
      context.put(CommandContext.VALUE, value);
      context.put(CommandContext.EXPIRY, expiry);
      context.put(CommandContext.COMMAND_ID, commandID);
      Command command = commandFact.getCommand(commandID);
      boolean ret = exec(command, context);
      if (ret) {
        return (CasResponse) context.get(CommandContext.RESULT);
      } else {
        return null;
      }
    } catch (ClientException e) {
      LOG.error("cas failed: " + key, e);
      throw e;
    }
  }

  public boolean exec(Command command, CommandContext context)
      throws ClientException {
    return command.execute(context);
  }
}