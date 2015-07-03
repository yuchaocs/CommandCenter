/**
 * Autogenerated by Thrift Compiler (0.9.2)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package edu.umich.clarity.thrift;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked"})
@Generated(value = "Autogenerated by Thrift Compiler (0.9.2)", date = "2015-7-3")
public class NodeManagerService {

  public interface Iface {

    public edu.umich.clarity.thrift.THostPort launchServiceInstance(String serviceType, double budget) throws org.apache.thrift.TException;

  }

  public interface AsyncIface {

    public void launchServiceInstance(String serviceType, double budget, org.apache.thrift.async.AsyncMethodCallback resultHandler) throws org.apache.thrift.TException;

  }

  public static class Client extends org.apache.thrift.TServiceClient implements Iface {
    public static class Factory implements org.apache.thrift.TServiceClientFactory<Client> {
      public Factory() {}
      public Client getClient(org.apache.thrift.protocol.TProtocol prot) {
        return new Client(prot);
      }
      public Client getClient(org.apache.thrift.protocol.TProtocol iprot, org.apache.thrift.protocol.TProtocol oprot) {
        return new Client(iprot, oprot);
      }
    }

    public Client(org.apache.thrift.protocol.TProtocol prot)
    {
      super(prot, prot);
    }

    public Client(org.apache.thrift.protocol.TProtocol iprot, org.apache.thrift.protocol.TProtocol oprot) {
      super(iprot, oprot);
    }

    public edu.umich.clarity.thrift.THostPort launchServiceInstance(String serviceType, double budget) throws org.apache.thrift.TException
    {
      send_launchServiceInstance(serviceType, budget);
      return recv_launchServiceInstance();
    }

    public void send_launchServiceInstance(String serviceType, double budget) throws org.apache.thrift.TException
    {
      launchServiceInstance_args args = new launchServiceInstance_args();
      args.setServiceType(serviceType);
      args.setBudget(budget);
      sendBase("launchServiceInstance", args);
    }

    public edu.umich.clarity.thrift.THostPort recv_launchServiceInstance() throws org.apache.thrift.TException
    {
      launchServiceInstance_result result = new launchServiceInstance_result();
      receiveBase(result, "launchServiceInstance");
      if (result.isSetSuccess()) {
        return result.success;
      }
      throw new org.apache.thrift.TApplicationException(org.apache.thrift.TApplicationException.MISSING_RESULT, "launchServiceInstance failed: unknown result");
    }

  }
  public static class AsyncClient extends org.apache.thrift.async.TAsyncClient implements AsyncIface {
    public static class Factory implements org.apache.thrift.async.TAsyncClientFactory<AsyncClient> {
      private org.apache.thrift.async.TAsyncClientManager clientManager;
      private org.apache.thrift.protocol.TProtocolFactory protocolFactory;
      public Factory(org.apache.thrift.async.TAsyncClientManager clientManager, org.apache.thrift.protocol.TProtocolFactory protocolFactory) {
        this.clientManager = clientManager;
        this.protocolFactory = protocolFactory;
      }
      public AsyncClient getAsyncClient(org.apache.thrift.transport.TNonblockingTransport transport) {
        return new AsyncClient(protocolFactory, clientManager, transport);
      }
    }

    public AsyncClient(org.apache.thrift.protocol.TProtocolFactory protocolFactory, org.apache.thrift.async.TAsyncClientManager clientManager, org.apache.thrift.transport.TNonblockingTransport transport) {
      super(protocolFactory, clientManager, transport);
    }

    public void launchServiceInstance(String serviceType, double budget, org.apache.thrift.async.AsyncMethodCallback resultHandler) throws org.apache.thrift.TException {
      checkReady();
      launchServiceInstance_call method_call = new launchServiceInstance_call(serviceType, budget, resultHandler, this, ___protocolFactory, ___transport);
      this.___currentMethod = method_call;
      ___manager.call(method_call);
    }

    public static class launchServiceInstance_call extends org.apache.thrift.async.TAsyncMethodCall {
      private String serviceType;
      private double budget;
      public launchServiceInstance_call(String serviceType, double budget, org.apache.thrift.async.AsyncMethodCallback resultHandler, org.apache.thrift.async.TAsyncClient client, org.apache.thrift.protocol.TProtocolFactory protocolFactory, org.apache.thrift.transport.TNonblockingTransport transport) throws org.apache.thrift.TException {
        super(client, protocolFactory, transport, resultHandler, false);
        this.serviceType = serviceType;
        this.budget = budget;
      }

      public void write_args(org.apache.thrift.protocol.TProtocol prot) throws org.apache.thrift.TException {
        prot.writeMessageBegin(new org.apache.thrift.protocol.TMessage("launchServiceInstance", org.apache.thrift.protocol.TMessageType.CALL, 0));
        launchServiceInstance_args args = new launchServiceInstance_args();
        args.setServiceType(serviceType);
        args.setBudget(budget);
        args.write(prot);
        prot.writeMessageEnd();
      }

      public edu.umich.clarity.thrift.THostPort getResult() throws org.apache.thrift.TException {
        if (getState() != org.apache.thrift.async.TAsyncMethodCall.State.RESPONSE_READ) {
          throw new IllegalStateException("Method call not finished!");
        }
        org.apache.thrift.transport.TMemoryInputTransport memoryTransport = new org.apache.thrift.transport.TMemoryInputTransport(getFrameBuffer().array());
        org.apache.thrift.protocol.TProtocol prot = client.getProtocolFactory().getProtocol(memoryTransport);
        return (new Client(prot)).recv_launchServiceInstance();
      }
    }

  }

  public static class Processor<I extends Iface> extends org.apache.thrift.TBaseProcessor<I> implements org.apache.thrift.TProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class.getName());
    public Processor(I iface) {
      super(iface, getProcessMap(new HashMap<String, org.apache.thrift.ProcessFunction<I, ? extends org.apache.thrift.TBase>>()));
    }

    protected Processor(I iface, Map<String,  org.apache.thrift.ProcessFunction<I, ? extends  org.apache.thrift.TBase>> processMap) {
      super(iface, getProcessMap(processMap));
    }

    private static <I extends Iface> Map<String,  org.apache.thrift.ProcessFunction<I, ? extends  org.apache.thrift.TBase>> getProcessMap(Map<String,  org.apache.thrift.ProcessFunction<I, ? extends  org.apache.thrift.TBase>> processMap) {
      processMap.put("launchServiceInstance", new launchServiceInstance());
      return processMap;
    }

    public static class launchServiceInstance<I extends Iface> extends org.apache.thrift.ProcessFunction<I, launchServiceInstance_args> {
      public launchServiceInstance() {
        super("launchServiceInstance");
      }

      public launchServiceInstance_args getEmptyArgsInstance() {
        return new launchServiceInstance_args();
      }

      protected boolean isOneway() {
        return false;
      }

      public launchServiceInstance_result getResult(I iface, launchServiceInstance_args args) throws org.apache.thrift.TException {
        launchServiceInstance_result result = new launchServiceInstance_result();
        result.success = iface.launchServiceInstance(args.serviceType, args.budget);
        return result;
      }
    }

  }

  public static class AsyncProcessor<I extends AsyncIface> extends org.apache.thrift.TBaseAsyncProcessor<I> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncProcessor.class.getName());
    public AsyncProcessor(I iface) {
      super(iface, getProcessMap(new HashMap<String, org.apache.thrift.AsyncProcessFunction<I, ? extends org.apache.thrift.TBase, ?>>()));
    }

    protected AsyncProcessor(I iface, Map<String,  org.apache.thrift.AsyncProcessFunction<I, ? extends  org.apache.thrift.TBase, ?>> processMap) {
      super(iface, getProcessMap(processMap));
    }

    private static <I extends AsyncIface> Map<String,  org.apache.thrift.AsyncProcessFunction<I, ? extends  org.apache.thrift.TBase,?>> getProcessMap(Map<String,  org.apache.thrift.AsyncProcessFunction<I, ? extends  org.apache.thrift.TBase, ?>> processMap) {
      processMap.put("launchServiceInstance", new launchServiceInstance());
      return processMap;
    }

    public static class launchServiceInstance<I extends AsyncIface> extends org.apache.thrift.AsyncProcessFunction<I, launchServiceInstance_args, edu.umich.clarity.thrift.THostPort> {
      public launchServiceInstance() {
        super("launchServiceInstance");
      }

      public launchServiceInstance_args getEmptyArgsInstance() {
        return new launchServiceInstance_args();
      }

      public AsyncMethodCallback<edu.umich.clarity.thrift.THostPort> getResultHandler(final AsyncFrameBuffer fb, final int seqid) {
        final org.apache.thrift.AsyncProcessFunction fcall = this;
        return new AsyncMethodCallback<edu.umich.clarity.thrift.THostPort>() { 
          public void onComplete(edu.umich.clarity.thrift.THostPort o) {
            launchServiceInstance_result result = new launchServiceInstance_result();
            result.success = o;
            try {
              fcall.sendResponse(fb,result, org.apache.thrift.protocol.TMessageType.REPLY,seqid);
              return;
            } catch (Exception e) {
              LOGGER.error("Exception writing to internal frame buffer", e);
            }
            fb.close();
          }
          public void onError(Exception e) {
            byte msgType = org.apache.thrift.protocol.TMessageType.REPLY;
            org.apache.thrift.TBase msg;
            launchServiceInstance_result result = new launchServiceInstance_result();
            {
              msgType = org.apache.thrift.protocol.TMessageType.EXCEPTION;
              msg = (org.apache.thrift.TBase)new org.apache.thrift.TApplicationException(org.apache.thrift.TApplicationException.INTERNAL_ERROR, e.getMessage());
            }
            try {
              fcall.sendResponse(fb,msg,msgType,seqid);
              return;
            } catch (Exception ex) {
              LOGGER.error("Exception writing to internal frame buffer", ex);
            }
            fb.close();
          }
        };
      }

      protected boolean isOneway() {
        return false;
      }

      public void start(I iface, launchServiceInstance_args args, org.apache.thrift.async.AsyncMethodCallback<edu.umich.clarity.thrift.THostPort> resultHandler) throws TException {
        iface.launchServiceInstance(args.serviceType, args.budget,resultHandler);
      }
    }

  }

  public static class launchServiceInstance_args implements org.apache.thrift.TBase<launchServiceInstance_args, launchServiceInstance_args._Fields>, java.io.Serializable, Cloneable, Comparable<launchServiceInstance_args>   {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("launchServiceInstance_args");

    private static final org.apache.thrift.protocol.TField SERVICE_TYPE_FIELD_DESC = new org.apache.thrift.protocol.TField("serviceType", org.apache.thrift.protocol.TType.STRING, (short)1);
    private static final org.apache.thrift.protocol.TField BUDGET_FIELD_DESC = new org.apache.thrift.protocol.TField("budget", org.apache.thrift.protocol.TType.DOUBLE, (short)2);

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
    static {
      schemes.put(StandardScheme.class, new launchServiceInstance_argsStandardSchemeFactory());
      schemes.put(TupleScheme.class, new launchServiceInstance_argsTupleSchemeFactory());
    }

    public String serviceType; // required
    public double budget; // required

    /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      SERVICE_TYPE((short)1, "serviceType"),
      BUDGET((short)2, "budget");

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, or null if its not found.
       */
      public static _Fields findByThriftId(int fieldId) {
        switch(fieldId) {
          case 1: // SERVICE_TYPE
            return SERVICE_TYPE;
          case 2: // BUDGET
            return BUDGET;
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception
       * if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /**
       * Find the _Fields constant that matches name, or null if its not found.
       */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    // isset id assignments
    private static final int __BUDGET_ISSET_ID = 0;
    private byte __isset_bitfield = 0;
    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      tmpMap.put(_Fields.SERVICE_TYPE, new org.apache.thrift.meta_data.FieldMetaData("serviceType", org.apache.thrift.TFieldRequirementType.DEFAULT, 
          new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
      tmpMap.put(_Fields.BUDGET, new org.apache.thrift.meta_data.FieldMetaData("budget", org.apache.thrift.TFieldRequirementType.DEFAULT, 
          new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.DOUBLE)));
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(launchServiceInstance_args.class, metaDataMap);
    }

    public launchServiceInstance_args() {
    }

    public launchServiceInstance_args(
      String serviceType,
      double budget)
    {
      this();
      this.serviceType = serviceType;
      this.budget = budget;
      setBudgetIsSet(true);
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public launchServiceInstance_args(launchServiceInstance_args other) {
      __isset_bitfield = other.__isset_bitfield;
      if (other.isSetServiceType()) {
        this.serviceType = other.serviceType;
      }
      this.budget = other.budget;
    }

    public launchServiceInstance_args deepCopy() {
      return new launchServiceInstance_args(this);
    }

    @Override
    public void clear() {
      this.serviceType = null;
      setBudgetIsSet(false);
      this.budget = 0.0;
    }

    public String getServiceType() {
      return this.serviceType;
    }

    public launchServiceInstance_args setServiceType(String serviceType) {
      this.serviceType = serviceType;
      return this;
    }

    public void unsetServiceType() {
      this.serviceType = null;
    }

    /** Returns true if field serviceType is set (has been assigned a value) and false otherwise */
    public boolean isSetServiceType() {
      return this.serviceType != null;
    }

    public void setServiceTypeIsSet(boolean value) {
      if (!value) {
        this.serviceType = null;
      }
    }

    public double getBudget() {
      return this.budget;
    }

    public launchServiceInstance_args setBudget(double budget) {
      this.budget = budget;
      setBudgetIsSet(true);
      return this;
    }

    public void unsetBudget() {
      __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __BUDGET_ISSET_ID);
    }

    /** Returns true if field budget is set (has been assigned a value) and false otherwise */
    public boolean isSetBudget() {
      return EncodingUtils.testBit(__isset_bitfield, __BUDGET_ISSET_ID);
    }

    public void setBudgetIsSet(boolean value) {
      __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __BUDGET_ISSET_ID, value);
    }

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
      case SERVICE_TYPE:
        if (value == null) {
          unsetServiceType();
        } else {
          setServiceType((String)value);
        }
        break;

      case BUDGET:
        if (value == null) {
          unsetBudget();
        } else {
          setBudget((Double)value);
        }
        break;

      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
      case SERVICE_TYPE:
        return getServiceType();

      case BUDGET:
        return Double.valueOf(getBudget());

      }
      throw new IllegalStateException();
    }

    /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
      case SERVICE_TYPE:
        return isSetServiceType();
      case BUDGET:
        return isSetBudget();
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null)
        return false;
      if (that instanceof launchServiceInstance_args)
        return this.equals((launchServiceInstance_args)that);
      return false;
    }

    public boolean equals(launchServiceInstance_args that) {
      if (that == null)
        return false;

      boolean this_present_serviceType = true && this.isSetServiceType();
      boolean that_present_serviceType = true && that.isSetServiceType();
      if (this_present_serviceType || that_present_serviceType) {
        if (!(this_present_serviceType && that_present_serviceType))
          return false;
        if (!this.serviceType.equals(that.serviceType))
          return false;
      }

      boolean this_present_budget = true;
      boolean that_present_budget = true;
      if (this_present_budget || that_present_budget) {
        if (!(this_present_budget && that_present_budget))
          return false;
        if (this.budget != that.budget)
          return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      List<Object> list = new ArrayList<Object>();

      boolean present_serviceType = true && (isSetServiceType());
      list.add(present_serviceType);
      if (present_serviceType)
        list.add(serviceType);

      boolean present_budget = true;
      list.add(present_budget);
      if (present_budget)
        list.add(budget);

      return list.hashCode();
    }

    @Override
    public int compareTo(launchServiceInstance_args other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      lastComparison = Boolean.valueOf(isSetServiceType()).compareTo(other.isSetServiceType());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetServiceType()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.serviceType, other.serviceType);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      lastComparison = Boolean.valueOf(isSetBudget()).compareTo(other.isSetBudget());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetBudget()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.budget, other.budget);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("launchServiceInstance_args(");
      boolean first = true;

      sb.append("serviceType:");
      if (this.serviceType == null) {
        sb.append("null");
      } else {
        sb.append(this.serviceType);
      }
      first = false;
      if (!first) sb.append(", ");
      sb.append("budget:");
      sb.append(this.budget);
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
      try {
        // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
        __isset_bitfield = 0;
        read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class launchServiceInstance_argsStandardSchemeFactory implements SchemeFactory {
      public launchServiceInstance_argsStandardScheme getScheme() {
        return new launchServiceInstance_argsStandardScheme();
      }
    }

    private static class launchServiceInstance_argsStandardScheme extends StandardScheme<launchServiceInstance_args> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, launchServiceInstance_args struct) throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true)
        {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
            break;
          }
          switch (schemeField.id) {
            case 1: // SERVICE_TYPE
              if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                struct.serviceType = iprot.readString();
                struct.setServiceTypeIsSet(true);
              } else { 
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            case 2: // BUDGET
              if (schemeField.type == org.apache.thrift.protocol.TType.DOUBLE) {
                struct.budget = iprot.readDouble();
                struct.setBudgetIsSet(true);
              } else { 
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, launchServiceInstance_args struct) throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        if (struct.serviceType != null) {
          oprot.writeFieldBegin(SERVICE_TYPE_FIELD_DESC);
          oprot.writeString(struct.serviceType);
          oprot.writeFieldEnd();
        }
        oprot.writeFieldBegin(BUDGET_FIELD_DESC);
        oprot.writeDouble(struct.budget);
        oprot.writeFieldEnd();
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }

    }

    private static class launchServiceInstance_argsTupleSchemeFactory implements SchemeFactory {
      public launchServiceInstance_argsTupleScheme getScheme() {
        return new launchServiceInstance_argsTupleScheme();
      }
    }

    private static class launchServiceInstance_argsTupleScheme extends TupleScheme<launchServiceInstance_args> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, launchServiceInstance_args struct) throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
        BitSet optionals = new BitSet();
        if (struct.isSetServiceType()) {
          optionals.set(0);
        }
        if (struct.isSetBudget()) {
          optionals.set(1);
        }
        oprot.writeBitSet(optionals, 2);
        if (struct.isSetServiceType()) {
          oprot.writeString(struct.serviceType);
        }
        if (struct.isSetBudget()) {
          oprot.writeDouble(struct.budget);
        }
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, launchServiceInstance_args struct) throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
        BitSet incoming = iprot.readBitSet(2);
        if (incoming.get(0)) {
          struct.serviceType = iprot.readString();
          struct.setServiceTypeIsSet(true);
        }
        if (incoming.get(1)) {
          struct.budget = iprot.readDouble();
          struct.setBudgetIsSet(true);
        }
      }
    }

  }

  public static class launchServiceInstance_result implements org.apache.thrift.TBase<launchServiceInstance_result, launchServiceInstance_result._Fields>, java.io.Serializable, Cloneable, Comparable<launchServiceInstance_result>   {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("launchServiceInstance_result");

    private static final org.apache.thrift.protocol.TField SUCCESS_FIELD_DESC = new org.apache.thrift.protocol.TField("success", org.apache.thrift.protocol.TType.STRUCT, (short)0);

    private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
    static {
      schemes.put(StandardScheme.class, new launchServiceInstance_resultStandardSchemeFactory());
      schemes.put(TupleScheme.class, new launchServiceInstance_resultTupleSchemeFactory());
    }

    public edu.umich.clarity.thrift.THostPort success; // required

    /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
      SUCCESS((short)0, "success");

      private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

      static {
        for (_Fields field : EnumSet.allOf(_Fields.class)) {
          byName.put(field.getFieldName(), field);
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, or null if its not found.
       */
      public static _Fields findByThriftId(int fieldId) {
        switch(fieldId) {
          case 0: // SUCCESS
            return SUCCESS;
          default:
            return null;
        }
      }

      /**
       * Find the _Fields constant that matches fieldId, throwing an exception
       * if it is not found.
       */
      public static _Fields findByThriftIdOrThrow(int fieldId) {
        _Fields fields = findByThriftId(fieldId);
        if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
        return fields;
      }

      /**
       * Find the _Fields constant that matches name, or null if its not found.
       */
      public static _Fields findByName(String name) {
        return byName.get(name);
      }

      private final short _thriftId;
      private final String _fieldName;

      _Fields(short thriftId, String fieldName) {
        _thriftId = thriftId;
        _fieldName = fieldName;
      }

      public short getThriftFieldId() {
        return _thriftId;
      }

      public String getFieldName() {
        return _fieldName;
      }
    }

    // isset id assignments
    public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    static {
      Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
      tmpMap.put(_Fields.SUCCESS, new org.apache.thrift.meta_data.FieldMetaData("success", org.apache.thrift.TFieldRequirementType.DEFAULT, 
          new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, edu.umich.clarity.thrift.THostPort.class)));
      metaDataMap = Collections.unmodifiableMap(tmpMap);
      org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(launchServiceInstance_result.class, metaDataMap);
    }

    public launchServiceInstance_result() {
    }

    public launchServiceInstance_result(
      edu.umich.clarity.thrift.THostPort success)
    {
      this();
      this.success = success;
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public launchServiceInstance_result(launchServiceInstance_result other) {
      if (other.isSetSuccess()) {
        this.success = new edu.umich.clarity.thrift.THostPort(other.success);
      }
    }

    public launchServiceInstance_result deepCopy() {
      return new launchServiceInstance_result(this);
    }

    @Override
    public void clear() {
      this.success = null;
    }

    public edu.umich.clarity.thrift.THostPort getSuccess() {
      return this.success;
    }

    public launchServiceInstance_result setSuccess(edu.umich.clarity.thrift.THostPort success) {
      this.success = success;
      return this;
    }

    public void unsetSuccess() {
      this.success = null;
    }

    /** Returns true if field success is set (has been assigned a value) and false otherwise */
    public boolean isSetSuccess() {
      return this.success != null;
    }

    public void setSuccessIsSet(boolean value) {
      if (!value) {
        this.success = null;
      }
    }

    public void setFieldValue(_Fields field, Object value) {
      switch (field) {
      case SUCCESS:
        if (value == null) {
          unsetSuccess();
        } else {
          setSuccess((edu.umich.clarity.thrift.THostPort)value);
        }
        break;

      }
    }

    public Object getFieldValue(_Fields field) {
      switch (field) {
      case SUCCESS:
        return getSuccess();

      }
      throw new IllegalStateException();
    }

    /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
    public boolean isSet(_Fields field) {
      if (field == null) {
        throw new IllegalArgumentException();
      }

      switch (field) {
      case SUCCESS:
        return isSetSuccess();
      }
      throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
      if (that == null)
        return false;
      if (that instanceof launchServiceInstance_result)
        return this.equals((launchServiceInstance_result)that);
      return false;
    }

    public boolean equals(launchServiceInstance_result that) {
      if (that == null)
        return false;

      boolean this_present_success = true && this.isSetSuccess();
      boolean that_present_success = true && that.isSetSuccess();
      if (this_present_success || that_present_success) {
        if (!(this_present_success && that_present_success))
          return false;
        if (!this.success.equals(that.success))
          return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      List<Object> list = new ArrayList<Object>();

      boolean present_success = true && (isSetSuccess());
      list.add(present_success);
      if (present_success)
        list.add(success);

      return list.hashCode();
    }

    @Override
    public int compareTo(launchServiceInstance_result other) {
      if (!getClass().equals(other.getClass())) {
        return getClass().getName().compareTo(other.getClass().getName());
      }

      int lastComparison = 0;

      lastComparison = Boolean.valueOf(isSetSuccess()).compareTo(other.isSetSuccess());
      if (lastComparison != 0) {
        return lastComparison;
      }
      if (isSetSuccess()) {
        lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.success, other.success);
        if (lastComparison != 0) {
          return lastComparison;
        }
      }
      return 0;
    }

    public _Fields fieldForId(int fieldId) {
      return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
      schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
      schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
      }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("launchServiceInstance_result(");
      boolean first = true;

      sb.append("success:");
      if (this.success == null) {
        sb.append("null");
      } else {
        sb.append(this.success);
      }
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
      // check for required fields
      // check for sub-struct validity
      if (success != null) {
        success.validate();
      }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      try {
        write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
      try {
        read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
      } catch (org.apache.thrift.TException te) {
        throw new java.io.IOException(te);
      }
    }

    private static class launchServiceInstance_resultStandardSchemeFactory implements SchemeFactory {
      public launchServiceInstance_resultStandardScheme getScheme() {
        return new launchServiceInstance_resultStandardScheme();
      }
    }

    private static class launchServiceInstance_resultStandardScheme extends StandardScheme<launchServiceInstance_result> {

      public void read(org.apache.thrift.protocol.TProtocol iprot, launchServiceInstance_result struct) throws org.apache.thrift.TException {
        org.apache.thrift.protocol.TField schemeField;
        iprot.readStructBegin();
        while (true)
        {
          schemeField = iprot.readFieldBegin();
          if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
            break;
          }
          switch (schemeField.id) {
            case 0: // SUCCESS
              if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
                struct.success = new edu.umich.clarity.thrift.THostPort();
                struct.success.read(iprot);
                struct.setSuccessIsSet(true);
              } else { 
                org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
              }
              break;
            default:
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
          }
          iprot.readFieldEnd();
        }
        iprot.readStructEnd();

        // check for required fields of primitive type, which can't be checked in the validate method
        struct.validate();
      }

      public void write(org.apache.thrift.protocol.TProtocol oprot, launchServiceInstance_result struct) throws org.apache.thrift.TException {
        struct.validate();

        oprot.writeStructBegin(STRUCT_DESC);
        if (struct.success != null) {
          oprot.writeFieldBegin(SUCCESS_FIELD_DESC);
          struct.success.write(oprot);
          oprot.writeFieldEnd();
        }
        oprot.writeFieldStop();
        oprot.writeStructEnd();
      }

    }

    private static class launchServiceInstance_resultTupleSchemeFactory implements SchemeFactory {
      public launchServiceInstance_resultTupleScheme getScheme() {
        return new launchServiceInstance_resultTupleScheme();
      }
    }

    private static class launchServiceInstance_resultTupleScheme extends TupleScheme<launchServiceInstance_result> {

      @Override
      public void write(org.apache.thrift.protocol.TProtocol prot, launchServiceInstance_result struct) throws org.apache.thrift.TException {
        TTupleProtocol oprot = (TTupleProtocol) prot;
        BitSet optionals = new BitSet();
        if (struct.isSetSuccess()) {
          optionals.set(0);
        }
        oprot.writeBitSet(optionals, 1);
        if (struct.isSetSuccess()) {
          struct.success.write(oprot);
        }
      }

      @Override
      public void read(org.apache.thrift.protocol.TProtocol prot, launchServiceInstance_result struct) throws org.apache.thrift.TException {
        TTupleProtocol iprot = (TTupleProtocol) prot;
        BitSet incoming = iprot.readBitSet(1);
        if (incoming.get(0)) {
          struct.success = new edu.umich.clarity.thrift.THostPort();
          struct.success.read(iprot);
          struct.setSuccessIsSet(true);
        }
      }
    }

  }

}
