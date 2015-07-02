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
@Generated(value = "Autogenerated by Thrift Compiler (0.9.2)", date = "2015-7-2")
public class QuerySpec implements org.apache.thrift.TBase<QuerySpec, QuerySpec._Fields>, java.io.Serializable, Cloneable, Comparable<QuerySpec> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("QuerySpec");

  private static final org.apache.thrift.protocol.TField NAME_FIELD_DESC = new org.apache.thrift.protocol.TField("name", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField INPUT_FIELD_DESC = new org.apache.thrift.protocol.TField("input", org.apache.thrift.protocol.TType.STRING, (short)2);
  private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC = new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.LIST, (short)3);
  private static final org.apache.thrift.protocol.TField BUDGET_FIELD_DESC = new org.apache.thrift.protocol.TField("budget", org.apache.thrift.protocol.TType.DOUBLE, (short)4);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new QuerySpecStandardSchemeFactory());
    schemes.put(TupleScheme.class, new QuerySpecTupleSchemeFactory());
  }

  public String name; // optional
  public ByteBuffer input; // required
  public List<LatencySpec> timestamp; // required
  public double budget; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    NAME((short)1, "name"),
    INPUT((short)2, "input"),
    TIMESTAMP((short)3, "timestamp"),
    BUDGET((short)4, "budget");

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
        case 1: // NAME
          return NAME;
        case 2: // INPUT
          return INPUT;
        case 3: // TIMESTAMP
          return TIMESTAMP;
        case 4: // BUDGET
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
  private static final _Fields optionals[] = {_Fields.NAME};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.NAME, new org.apache.thrift.meta_data.FieldMetaData("name", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.INPUT, new org.apache.thrift.meta_data.FieldMetaData("input", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING        , true)));
    tmpMap.put(_Fields.TIMESTAMP, new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.ListMetaData(org.apache.thrift.protocol.TType.LIST, 
            new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT, LatencySpec.class))));
    tmpMap.put(_Fields.BUDGET, new org.apache.thrift.meta_data.FieldMetaData("budget", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.DOUBLE)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(QuerySpec.class, metaDataMap);
  }

  public QuerySpec() {
  }

  public QuerySpec(
    ByteBuffer input,
    List<LatencySpec> timestamp,
    double budget)
  {
    this();
    this.input = org.apache.thrift.TBaseHelper.copyBinary(input);
    this.timestamp = timestamp;
    this.budget = budget;
    setBudgetIsSet(true);
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public QuerySpec(QuerySpec other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetName()) {
      this.name = other.name;
    }
    if (other.isSetInput()) {
      this.input = org.apache.thrift.TBaseHelper.copyBinary(other.input);
    }
    if (other.isSetTimestamp()) {
      List<LatencySpec> __this__timestamp = new ArrayList<LatencySpec>(other.timestamp.size());
      for (LatencySpec other_element : other.timestamp) {
        __this__timestamp.add(new LatencySpec(other_element));
      }
      this.timestamp = __this__timestamp;
    }
    this.budget = other.budget;
  }

  public QuerySpec deepCopy() {
    return new QuerySpec(this);
  }

  @Override
  public void clear() {
    this.name = null;
    this.input = null;
    this.timestamp = null;
    setBudgetIsSet(false);
    this.budget = 0.0;
  }

  public String getName() {
    return this.name;
  }

  public QuerySpec setName(String name) {
    this.name = name;
    return this;
  }

  public void unsetName() {
    this.name = null;
  }

  /** Returns true if field name is set (has been assigned a value) and false otherwise */
  public boolean isSetName() {
    return this.name != null;
  }

  public void setNameIsSet(boolean value) {
    if (!value) {
      this.name = null;
    }
  }

  public byte[] getInput() {
    setInput(org.apache.thrift.TBaseHelper.rightSize(input));
    return input == null ? null : input.array();
  }

  public ByteBuffer bufferForInput() {
    return org.apache.thrift.TBaseHelper.copyBinary(input);
  }

  public QuerySpec setInput(byte[] input) {
    this.input = input == null ? (ByteBuffer)null : ByteBuffer.wrap(Arrays.copyOf(input, input.length));
    return this;
  }

  public QuerySpec setInput(ByteBuffer input) {
    this.input = org.apache.thrift.TBaseHelper.copyBinary(input);
    return this;
  }

  public void unsetInput() {
    this.input = null;
  }

  /** Returns true if field input is set (has been assigned a value) and false otherwise */
  public boolean isSetInput() {
    return this.input != null;
  }

  public void setInputIsSet(boolean value) {
    if (!value) {
      this.input = null;
    }
  }

  public int getTimestampSize() {
    return (this.timestamp == null) ? 0 : this.timestamp.size();
  }

  public java.util.Iterator<LatencySpec> getTimestampIterator() {
    return (this.timestamp == null) ? null : this.timestamp.iterator();
  }

  public void addToTimestamp(LatencySpec elem) {
    if (this.timestamp == null) {
      this.timestamp = new ArrayList<LatencySpec>();
    }
    this.timestamp.add(elem);
  }

  public List<LatencySpec> getTimestamp() {
    return this.timestamp;
  }

  public QuerySpec setTimestamp(List<LatencySpec> timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public void unsetTimestamp() {
    this.timestamp = null;
  }

  /** Returns true if field timestamp is set (has been assigned a value) and false otherwise */
  public boolean isSetTimestamp() {
    return this.timestamp != null;
  }

  public void setTimestampIsSet(boolean value) {
    if (!value) {
      this.timestamp = null;
    }
  }

  public double getBudget() {
    return this.budget;
  }

  public QuerySpec setBudget(double budget) {
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
    case NAME:
      if (value == null) {
        unsetName();
      } else {
        setName((String)value);
      }
      break;

    case INPUT:
      if (value == null) {
        unsetInput();
      } else {
        setInput((ByteBuffer)value);
      }
      break;

    case TIMESTAMP:
      if (value == null) {
        unsetTimestamp();
      } else {
        setTimestamp((List<LatencySpec>)value);
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
    case NAME:
      return getName();

    case INPUT:
      return getInput();

    case TIMESTAMP:
      return getTimestamp();

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
    case NAME:
      return isSetName();
    case INPUT:
      return isSetInput();
    case TIMESTAMP:
      return isSetTimestamp();
    case BUDGET:
      return isSetBudget();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof QuerySpec)
      return this.equals((QuerySpec)that);
    return false;
  }

  public boolean equals(QuerySpec that) {
    if (that == null)
      return false;

    boolean this_present_name = true && this.isSetName();
    boolean that_present_name = true && that.isSetName();
    if (this_present_name || that_present_name) {
      if (!(this_present_name && that_present_name))
        return false;
      if (!this.name.equals(that.name))
        return false;
    }

    boolean this_present_input = true && this.isSetInput();
    boolean that_present_input = true && that.isSetInput();
    if (this_present_input || that_present_input) {
      if (!(this_present_input && that_present_input))
        return false;
      if (!this.input.equals(that.input))
        return false;
    }

    boolean this_present_timestamp = true && this.isSetTimestamp();
    boolean that_present_timestamp = true && that.isSetTimestamp();
    if (this_present_timestamp || that_present_timestamp) {
      if (!(this_present_timestamp && that_present_timestamp))
        return false;
      if (!this.timestamp.equals(that.timestamp))
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

    boolean present_name = true && (isSetName());
    list.add(present_name);
    if (present_name)
      list.add(name);

    boolean present_input = true && (isSetInput());
    list.add(present_input);
    if (present_input)
      list.add(input);

    boolean present_timestamp = true && (isSetTimestamp());
    list.add(present_timestamp);
    if (present_timestamp)
      list.add(timestamp);

    boolean present_budget = true;
    list.add(present_budget);
    if (present_budget)
      list.add(budget);

    return list.hashCode();
  }

  @Override
  public int compareTo(QuerySpec other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetName()).compareTo(other.isSetName());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetName()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.name, other.name);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetInput()).compareTo(other.isSetInput());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetInput()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.input, other.input);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetTimestamp()).compareTo(other.isSetTimestamp());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetTimestamp()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.timestamp, other.timestamp);
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
    StringBuilder sb = new StringBuilder("QuerySpec(");
    boolean first = true;

    if (isSetName()) {
      sb.append("name:");
      if (this.name == null) {
        sb.append("null");
      } else {
        sb.append(this.name);
      }
      first = false;
    }
    if (!first) sb.append(", ");
    sb.append("input:");
    if (this.input == null) {
      sb.append("null");
    } else {
      org.apache.thrift.TBaseHelper.toString(this.input, sb);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("timestamp:");
    if (this.timestamp == null) {
      sb.append("null");
    } else {
      sb.append(this.timestamp);
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

  private static class QuerySpecStandardSchemeFactory implements SchemeFactory {
    public QuerySpecStandardScheme getScheme() {
      return new QuerySpecStandardScheme();
    }
  }

  private static class QuerySpecStandardScheme extends StandardScheme<QuerySpec> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, QuerySpec struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // NAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.name = iprot.readString();
              struct.setNameIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // INPUT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.input = iprot.readBinary();
              struct.setInputIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // TIMESTAMP
            if (schemeField.type == org.apache.thrift.protocol.TType.LIST) {
              {
                org.apache.thrift.protocol.TList _list0 = iprot.readListBegin();
                struct.timestamp = new ArrayList<LatencySpec>(_list0.size);
                LatencySpec _elem1;
                for (int _i2 = 0; _i2 < _list0.size; ++_i2)
                {
                  _elem1 = new LatencySpec();
                  _elem1.read(iprot);
                  struct.timestamp.add(_elem1);
                }
                iprot.readListEnd();
              }
              struct.setTimestampIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // BUDGET
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

    public void write(org.apache.thrift.protocol.TProtocol oprot, QuerySpec struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.name != null) {
        if (struct.isSetName()) {
          oprot.writeFieldBegin(NAME_FIELD_DESC);
          oprot.writeString(struct.name);
          oprot.writeFieldEnd();
        }
      }
      if (struct.input != null) {
        oprot.writeFieldBegin(INPUT_FIELD_DESC);
        oprot.writeBinary(struct.input);
        oprot.writeFieldEnd();
      }
      if (struct.timestamp != null) {
        oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
        {
          oprot.writeListBegin(new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, struct.timestamp.size()));
          for (LatencySpec _iter3 : struct.timestamp)
          {
            _iter3.write(oprot);
          }
          oprot.writeListEnd();
        }
        oprot.writeFieldEnd();
      }
      oprot.writeFieldBegin(BUDGET_FIELD_DESC);
      oprot.writeDouble(struct.budget);
      oprot.writeFieldEnd();
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class QuerySpecTupleSchemeFactory implements SchemeFactory {
    public QuerySpecTupleScheme getScheme() {
      return new QuerySpecTupleScheme();
    }
  }

  private static class QuerySpecTupleScheme extends TupleScheme<QuerySpec> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, QuerySpec struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetName()) {
        optionals.set(0);
      }
      if (struct.isSetInput()) {
        optionals.set(1);
      }
      if (struct.isSetTimestamp()) {
        optionals.set(2);
      }
      if (struct.isSetBudget()) {
        optionals.set(3);
      }
      oprot.writeBitSet(optionals, 4);
      if (struct.isSetName()) {
        oprot.writeString(struct.name);
      }
      if (struct.isSetInput()) {
        oprot.writeBinary(struct.input);
      }
      if (struct.isSetTimestamp()) {
        {
          oprot.writeI32(struct.timestamp.size());
          for (LatencySpec _iter4 : struct.timestamp)
          {
            _iter4.write(oprot);
          }
        }
      }
      if (struct.isSetBudget()) {
        oprot.writeDouble(struct.budget);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, QuerySpec struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(4);
      if (incoming.get(0)) {
        struct.name = iprot.readString();
        struct.setNameIsSet(true);
      }
      if (incoming.get(1)) {
        struct.input = iprot.readBinary();
        struct.setInputIsSet(true);
      }
      if (incoming.get(2)) {
        {
          org.apache.thrift.protocol.TList _list5 = new org.apache.thrift.protocol.TList(org.apache.thrift.protocol.TType.STRUCT, iprot.readI32());
          struct.timestamp = new ArrayList<LatencySpec>(_list5.size);
          LatencySpec _elem6;
          for (int _i7 = 0; _i7 < _list5.size; ++_i7)
          {
            _elem6 = new LatencySpec();
            _elem6.read(iprot);
            struct.timestamp.add(_elem6);
          }
        }
        struct.setTimestampIsSet(true);
      }
      if (incoming.get(3)) {
        struct.budget = iprot.readDouble();
        struct.setBudgetIsSet(true);
      }
    }
  }

}

