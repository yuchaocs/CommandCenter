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
@Generated(value = "Autogenerated by Thrift Compiler (0.9.2)", date = "2015-6-30")
public class RegMessage implements org.apache.thrift.TBase<RegMessage, RegMessage._Fields>, java.io.Serializable, Cloneable, Comparable<RegMessage> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("RegMessage");

  private static final org.apache.thrift.protocol.TField APP_NAME_FIELD_DESC = new org.apache.thrift.protocol.TField("app_name", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField ENDPOINT_FIELD_DESC = new org.apache.thrift.protocol.TField("endpoint", org.apache.thrift.protocol.TType.STRUCT, (short)2);
  private static final org.apache.thrift.protocol.TField BUDGET_FIELD_DESC = new org.apache.thrift.protocol.TField("budget", org.apache.thrift.protocol.TType.DOUBLE, (short)3);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new RegMessageStandardSchemeFactory());
    schemes.put(TupleScheme.class, new RegMessageTupleSchemeFactory());
  }

  public String app_name; // required
  public THostPort endpoint; // required
  public double budget; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    APP_NAME((short)1, "app_name"),
    ENDPOINT((short)2, "endpoint"),
    BUDGET((short)3, "budget");

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
        case 1: // APP_NAME
          return APP_NAME;
        case 2: // ENDPOINT
          return ENDPOINT;
        case 3: // BUDGET
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
    tmpMap.put(_Fields.APP_NAME, new org.apache.thrift.meta_data.FieldMetaData("app_name", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.ENDPOINT, new org.apache.thrift.meta_data.FieldMetaData("endpoint", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, THostPort.class)));
    tmpMap.put(_Fields.BUDGET, new org.apache.thrift.meta_data.FieldMetaData("budget", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.DOUBLE)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(RegMessage.class, metaDataMap);
  }

  public RegMessage() {
  }

  public RegMessage(
    String app_name,
    THostPort endpoint,
    double budget)
  {
    this();
    this.app_name = app_name;
    this.endpoint = endpoint;
    this.budget = budget;
    setBudgetIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public RegMessage(RegMessage other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetApp_name()) {
      this.app_name = other.app_name;
    }
    if (other.isSetEndpoint()) {
      this.endpoint = new THostPort(other.endpoint);
    }
    this.budget = other.budget;
  }

  public RegMessage deepCopy() {
    return new RegMessage(this);
  }

  @Override
  public void clear() {
    this.app_name = null;
    this.endpoint = null;
    setBudgetIsSet(false);
    this.budget = 0.0;
  }

  public String getApp_name() {
    return this.app_name;
  }

  public RegMessage setApp_name(String app_name) {
    this.app_name = app_name;
    return this;
  }

  public void unsetApp_name() {
    this.app_name = null;
  }

  /** Returns true if field app_name is set (has been assigned a value) and false otherwise */
  public boolean isSetApp_name() {
    return this.app_name != null;
  }

  public void setApp_nameIsSet(boolean value) {
    if (!value) {
      this.app_name = null;
    }
  }

  public THostPort getEndpoint() {
    return this.endpoint;
  }

  public RegMessage setEndpoint(THostPort endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public void unsetEndpoint() {
    this.endpoint = null;
  }

  /** Returns true if field endpoint is set (has been assigned a value) and false otherwise */
  public boolean isSetEndpoint() {
    return this.endpoint != null;
  }

  public void setEndpointIsSet(boolean value) {
    if (!value) {
      this.endpoint = null;
    }
  }

  public double getBudget() {
    return this.budget;
  }

  public RegMessage setBudget(double budget) {
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
    case APP_NAME:
      if (value == null) {
        unsetApp_name();
      } else {
        setApp_name((String)value);
      }
      break;

    case ENDPOINT:
      if (value == null) {
        unsetEndpoint();
      } else {
        setEndpoint((THostPort)value);
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
    case APP_NAME:
      return getApp_name();

    case ENDPOINT:
      return getEndpoint();

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
    case APP_NAME:
      return isSetApp_name();
    case ENDPOINT:
      return isSetEndpoint();
    case BUDGET:
      return isSetBudget();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof RegMessage)
      return this.equals((RegMessage)that);
    return false;
  }

  public boolean equals(RegMessage that) {
    if (that == null)
      return false;

    boolean this_present_app_name = true && this.isSetApp_name();
    boolean that_present_app_name = true && that.isSetApp_name();
    if (this_present_app_name || that_present_app_name) {
      if (!(this_present_app_name && that_present_app_name))
        return false;
      if (!this.app_name.equals(that.app_name))
        return false;
    }

    boolean this_present_endpoint = true && this.isSetEndpoint();
    boolean that_present_endpoint = true && that.isSetEndpoint();
    if (this_present_endpoint || that_present_endpoint) {
      if (!(this_present_endpoint && that_present_endpoint))
        return false;
      if (!this.endpoint.equals(that.endpoint))
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

    boolean present_app_name = true && (isSetApp_name());
    list.add(present_app_name);
    if (present_app_name)
      list.add(app_name);

    boolean present_endpoint = true && (isSetEndpoint());
    list.add(present_endpoint);
    if (present_endpoint)
      list.add(endpoint);

    boolean present_budget = true;
    list.add(present_budget);
    if (present_budget)
      list.add(budget);

    return list.hashCode();
  }

  @Override
  public int compareTo(RegMessage other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetApp_name()).compareTo(other.isSetApp_name());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetApp_name()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.app_name, other.app_name);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetEndpoint()).compareTo(other.isSetEndpoint());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetEndpoint()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.endpoint, other.endpoint);
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
    StringBuilder sb = new StringBuilder("RegMessage(");
    boolean first = true;

    sb.append("app_name:");
    if (this.app_name == null) {
      sb.append("null");
    } else {
      sb.append(this.app_name);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("endpoint:");
    if (this.endpoint == null) {
      sb.append("null");
    } else {
      sb.append(this.endpoint);
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
    if (endpoint != null) {
      endpoint.validate();
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
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class RegMessageStandardSchemeFactory implements SchemeFactory {
    public RegMessageStandardScheme getScheme() {
      return new RegMessageStandardScheme();
    }
  }

  private static class RegMessageStandardScheme extends StandardScheme<RegMessage> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, RegMessage struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // APP_NAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.app_name = iprot.readString();
              struct.setApp_nameIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // ENDPOINT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
              struct.endpoint = new THostPort();
              struct.endpoint.read(iprot);
              struct.setEndpointIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // BUDGET
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, RegMessage struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.app_name != null) {
        oprot.writeFieldBegin(APP_NAME_FIELD_DESC);
        oprot.writeString(struct.app_name);
        oprot.writeFieldEnd();
      }
      if (struct.endpoint != null) {
        oprot.writeFieldBegin(ENDPOINT_FIELD_DESC);
        struct.endpoint.write(oprot);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(BUDGET_FIELD_DESC);
      oprot.writeDouble(struct.budget);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class RegMessageTupleSchemeFactory implements SchemeFactory {
    public RegMessageTupleScheme getScheme() {
      return new RegMessageTupleScheme();
    }
  }

  private static class RegMessageTupleScheme extends TupleScheme<RegMessage> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, RegMessage struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetApp_name()) {
        optionals.set(0);
      }
      if (struct.isSetEndpoint()) {
        optionals.set(1);
      }
      if (struct.isSetBudget()) {
        optionals.set(2);
      }
      oprot.writeBitSet(optionals, 3);
      if (struct.isSetApp_name()) {
        oprot.writeString(struct.app_name);
      }
      if (struct.isSetEndpoint()) {
        struct.endpoint.write(oprot);
      }
      if (struct.isSetBudget()) {
        oprot.writeDouble(struct.budget);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, RegMessage struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(3);
      if (incoming.get(0)) {
        struct.app_name = iprot.readString();
        struct.setApp_nameIsSet(true);
      }
      if (incoming.get(1)) {
        struct.endpoint = new THostPort();
        struct.endpoint.read(iprot);
        struct.setEndpointIsSet(true);
      }
      if (incoming.get(2)) {
        struct.budget = iprot.readDouble();
        struct.setBudgetIsSet(true);
      }
    }
  }

}

