package gov.cdc.izgw.v2tofhir.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.UnsignedIntType;
import org.hl7.fhir.r4.model.UriType;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.Varies;
import ca.uhn.hl7v2.model.primitive.TSComponentOne;
import gov.cdc.izgw.v2tofhir.converter.datatype.AddressParser;
import gov.cdc.izgw.v2tofhir.converter.datatype.ContactPointParser;
import gov.cdc.izgw.v2tofhir.converter.datatype.HumanNameParser;
import lombok.extern.slf4j.Slf4j;

/**
 * DatatypeConverter is the entry point for V2 to FHIR datatype conversion.
 * 
 * Operations in DatatypeConverter
 * 
 * <ul>
 * <li><b>convert</b>
 * 		- Generic methods</li>
 * <li><b>getConverter/converter</b>
 * 		- Generic Functional Interfaces</li>
 * <li><b>to{FhirType}(V2 datatype)</b>
 * 		- Converts to a specified FHIR datatype</li>
 * <li><b>castInto()</b>
 * 		- Converts between FHIR Numeric types</li>
 * </ul>
 * 
 * Supported FHIR Types:
 * 
 * <ul>
 * <li>Address</li>
 * <li>CodeableConcept</li>
 * <li>CodeType</li>
 * <li>Coding</li>
 * <li>ContactPoint</li>
 * <li>DateTimeType</li>
 * <li>DateType</li>
 * <li>DecimalType</li>
 * <li>HumanName</li>
 * <li>Identifier</li>
 * <li>IdType</li>
 * <li>InstantType</li>
 * <li>IntegerType</li>
 * <li>PositiveIntType</li>
 * <li>Quantity</li>
 * <li>StringType</li>
 * <li>TimeType</li>
 * <li>UnsignedIntType</li>
 * <li>UriType</li>
 * <ul>
 * 
 * @see <a href="https://build.fhir.org/ig/HL7/v2-to-fhir/datatype_maps.html">HL7 Version 2 to FHIR - Datatype Maps</a>
 * @author Audacious Inquiry
 *
 */
@Slf4j
public class DatatypeConverter {
	private static final BigDecimal MAX_UNSIGNED_VALUE = new BigDecimal(Integer.MAX_VALUE);
	private static final AddressParser addressParser = new AddressParser();
	private static final ContactPointParser contactPointParser = new ContactPointParser();
	private static final HumanNameParser nameParser = new HumanNameParser();

	/**
	 * A functional interface for FHIR datatype conversion from HAPI V2 datatypes
	 * 
	 * @author Audacious Inquiry
	 *
	 * @param <F> A FHIR data type to convert to.
	 */
	@FunctionalInterface
	public interface Converter<F extends org.hl7.fhir.r4.model.Type> {
		/**
		 * Convert a V2 datatype to a FHIR datatype
		 * @param type	The V2 datatype to convert
		 * @return	The converted FHIR datatype
		 */
		F convert(Type type);
		
		/**
		 * Convert a V2 datatype to a FHIR datatype
		 * @param type	The V2 datatype to convert
		 * @param clazz The class of the FHIR object to conver to
		 * @return	The converted FHIR datatype
		 * @throws ClassCastException if the converted type is incorrect.
		 */
		default F convertAs(Class<F> clazz, Type type) {
			return clazz.cast(convert(type));
		}
	}

	private DatatypeConverter() {
	}

	/**
	 * Get a converter for a FHIR datatype
	 * @param <F>	The FHIR datatype
	 * @param clazz	The class representing the datatype
	 * @return The converter
	 */
	public static <F extends org.hl7.fhir.r4.model.Type> Converter<F> getConverter(Class<F> clazz) {
		return (Type t) -> convert(clazz, t);
	}

	/**
	 * Get a converter for a FHIR datatype
	 * @param className	The name of the FHIR datatype
	 * @return The converter
	 */
	public static <F extends org.hl7.fhir.r4.model.Type> Converter<F> getConverter(String className) {
		return (Type t) -> convert(className, t);
	}
	
	private static final Set<String> FHIR_PRIMITIVE_NAMES = new LinkedHashSet<>(
		Arrays.asList("integer", "string", "time", "date", "datetime", "decimal", "boolean", "url",
					  "code", "integer", "uri", "canonical", "markdown", "id", "oid", "uuid", 
					  "unsignedInt", "positiveInt"));

	/**
	 * Get a converter for a FHIR datatype.
	 * @param <F>	The FHIR type to convert to.
	 * @param className	The name of the FHIR datatype
	 * @param t	The HAP V2 type to convert
	 * @return The converter
	 */
	public static <F extends org.hl7.fhir.r4.model.Type> F convert(String className, Type t) {
		className =  "org.hl7.fhir.r4.model." + className;
		
		if (FHIR_PRIMITIVE_NAMES.contains(className)) {
			className = Character.toUpperCase(className.charAt(0)) + className.substring(1) + "Type";
		}
		try {
			@SuppressWarnings("unchecked")
			Class<F> clazz = (Class<F>) Type.class.getClassLoader().loadClass(className);
			return convert(clazz, t);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(className + " is not a supported FHIR type");
		}
	}

	/**
	 * Convert a HAPI V2 datatype to a FHIR datatype.
	 * 
	 * @param <F>	The FHIR datatype
	 * @param clazz	The class representing the FHIR datatype
	 * @param t	The HAPI V2 type to convert
	 * @return	The converted HAPI V2 type
	 */
	public static <F extends org.hl7.fhir.r4.model.Type> F convert(Class<F> clazz, Type t) {
		switch (clazz.getSimpleName()) {
		case "Address":
			return clazz.cast(toAddress(t));
		case "CodeableConcept":
			return clazz.cast(toCodeableConcept(t));
		case "CodeType":
			return clazz.cast(toCodeType(t));
		case "Coding":
			return clazz.cast(toCoding(t));
		case "ContactPoint":
			return clazz.cast(toContactPoint(t));
		case "DateTimeType":
			return clazz.cast(toDateTimeType(t));
		case "DateType":
			return clazz.cast(toDateType(t));
		case "DecimalType":
			return clazz.cast(toDecimalType(t));
		case "HumanName":
			return clazz.cast(toHumanName(t));
		case "Identifier":
			return clazz.cast(toIdentifier(t));
		case "IdType":
			return clazz.cast(toIdType(t));
		case "InstantType":
			return clazz.cast(toInstantType(t));
		case "IntegerType":
			return clazz.cast(toIntegerType(t));
		case "PositiveIntType":
			return clazz.cast(toPositiveIntType(t));
		case "Quantity":
			return clazz.cast(toQuantity(t));
		case "StringType":
			return clazz.cast(toStringType(t));
		case "TimeType":
			return clazz.cast(toTimeType(t));
		case "UnsignedIntType":
			return clazz.cast(toUnsignedIntType(t));
		case "UriType":
			return clazz.cast(toUriType(t));
		default:
			throw new IllegalArgumentException(clazz.getName() + " is not a supported FHIR type");
		}
	}


	/**
     * Convert a HAPI V2 datatype to a FHIR Address
     * @param codedElement The HAPI V2 type to convert
     * @return The Address converted from the V2 datatype
     */
    public static Address toAddress(Type codedElement) {
		return addressParser.convert(codedElement);
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR CodeableConcept
     * @param codedElement The HAPI V2 type to convert
     * @return The CodeableConcept converted from the V2 datatype
     */
    public static CodeableConcept toCodeableConcept(Type codedElement) {
		return toCodeableConcept(codedElement, null);
	}

	/**
	 * Used to add an element V to a field of type T in a FHIR type.
	 * 
	 * Example usage: 
	 *   CodeableConcept cc = new CodeableConcept();
	 *   Coding c = new Coding();
	 *   addValue(cc:addCoding, c);
	 *   
	 * @param <T>	The type to add or set
	 * @param consumer	An adder or setter method, e.g., cc::addCoding or cc::setValue
	 * @param t	The object to add or set.
	 */
	public static <T extends org.hl7.fhir.r4.model.Type> void addIfNotEmpty(Consumer<T> consumer, T t
	) {
		if (t != null && !t.isEmpty()) {
			consumer.accept(t);
		}
	}
	/**
     * Convert a HAPI V2 datatype to a FHIR CodeableConcept
     * @param codedElement The HAPI V2 type to convert
     * @param table The HL7 table or coding system to use for the system of the coded element
     * @return The CodeableConcept converted from the V2 datatype
     */
    public static CodeableConcept toCodeableConcept(Type codedElement, String table) {
		if ((codedElement = adjustIfVaries(codedElement)) == null) {
			return null;
		}
		CodeableConcept cc = new CodeableConcept();
		Primitive st = null;
		Composite comp = null;
		switch (codedElement.getName()) {
		case "CE", "CF", "CNE", "CWE":
			comp = (Composite) codedElement;
			for (int i = 0; i <= 3; i += 3) {
				addIfNotEmpty(cc::addCoding, getCoding(comp, i, true));
			}
			setValue(cc::setText, comp.getComponents(), 8);
			break;
		case "CX":
			Identifier ident = toIdentifier(codedElement);
			if (ident != null && !ident.isEmpty()) {
				addIfNotEmpty(cc::addCoding, new Coding(ident.getSystem(), ident.getValue(), null));
			}
			break;
		case "CQ":
			comp = (Composite) codedElement;
			Type[] types = comp.getComponents();
			if (types.length > 1) {
				return toCodeableConcept(types[1]);
			}
			break;
		case "EI", "EIP", "HD":
			ident = toIdentifier(codedElement);
			if (ident == null) {
				return null;
			}
			addIfNotEmpty(cc::addCoding, new Coding(ident.getSystem(), ident.getValue(), null));
			break;
		case "ID":
			st = (Primitive) codedElement;
			addIfNotEmpty(cc::addCoding, new Coding("http://terminology.hl7.org/CodeSystem/v2-0301", st.getValue(), null));
			break;

		case "IS", "ST":
			st = (Primitive) codedElement;
			addIfNotEmpty(cc::addCoding, Mapping.mapSystem(new Coding(table, st.getValue(), null)));
			break;
		default:
			break;
		}
		if (cc.isEmpty()) {
			return null;
		}
		return cc;
	}

	/**
	 * Convert a V2 Varies datatype to its actual datatype
	 * 
	 * Used internally in DatatypeConverter to process data
	 *  
	 * NOTE: This operation works on Varies objects where the datatype is specified elsewhere
	 * in the message (such as for OBX-5 where the type is specified in OBX-2).  Don't expect
	 * this to work well where the HAPI V2 Parser doesn't already know the type.
	 *  
	 * @param type	The V2 varies object to adjust
	 * @return	A V2 Primitive or Composite datatype
	 */
	public static Type adjustIfVaries(Type type) {
		if (type instanceof Varies v) {
			return v.getData();
		}
		return type;
	}
	
	/**
	 * Adjust the specified type in the component fields of a V2 Composite
	 *
	 * @param types	The types of the V2 composite
	 * @param index	The index of the type to adjust
	 * @return	The adjusted type from the types, or null if the component does not exist
	 * @see #adjustIfVaries(Type)
	 */
	public static Type adjustIfVaries(Type[] types, int index) {
		if (types == null || index < 0 || index >= types.length) {
			return null;
		}
		return adjustIfVaries(types[index]);
	}
	

	private static Identifier extractAsIdentifier(Composite comp, int idLocation, int checkDigitLoc, int idTypeLoc,
			int... systemValues) {
		if (comp == null) {
			return null;
		}
		Type[] types = comp.getComponents();
		Identifier id = new Identifier();
		String value = getValueOfIdentifier(idLocation, checkDigitLoc, types);
		id.setValue(value);

		for (int v : systemValues) {
			if (types.length > v && !ParserUtils.isEmpty(types[v])) {
				String system = getSystemOfIdentifier(types[v]);
				if (system != null) {
					id.setSystem(system);
					Mapping.mapSystem(id);
					if (id.getUserData("originalSystem") != null) {
						break;
					}
				}
			}
		}
		Type type = adjustIfVaries(types, idTypeLoc);
		if (type instanceof Primitive pt && !ParserUtils.isEmpty(pt)) {
			Coding coding = new Coding(Systems.IDENTIFIER_TYPE, pt.getValue(), null);
			Mapping.setDisplay(coding);
			CodeableConcept cc = new CodeableConcept();
			cc.addCoding(coding);
			id.setType(cc);
		}
		if (id.isEmpty()) {
			return null;
		}
		return id;
	}

	private static String getSystemOfIdentifier(Type type) {
		type = adjustIfVaries(type);
		if (type instanceof Primitive pt) {
			return pt.getValue();
		} else if (type instanceof Composite comp2 && "HD".equals(comp2.getName()) // NOSONAR Name check is correct here
		) {
			List<String> l = DatatypeConverter.getSystemsFromHD(0, comp2.getComponents());
			if (!l.isEmpty()) {
				return l.get(0);
			}
		}
		return null;
	}

	private static String getValueOfIdentifier(int idLocation, int checkDigitLoc, Type[] types) {
		Type ident = adjustIfVaries(types, idLocation);
		Type checkDigit = adjustIfVaries(types, checkDigitLoc);
		if (ident != null && !ParserUtils.isEmpty(ident)) {
			if (checkDigit != null && !ParserUtils.isEmpty(checkDigit)) {
				return ParserUtils.toString(ident) + "-" + ParserUtils.toString(checkDigit);
			}
			return ParserUtils.toString(ident);
		}
		return null;
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR CodeType
     * @param codedElement The HAPI V2 type to convert
     * @return The CodeType converted from the V2 datatype
     */
    public static CodeType toCodeType(Type codedElement) {
		if (codedElement == null) {
			return null;
		}
		codedElement = adjustIfVaries(codedElement);
		if (codedElement instanceof Primitive pt) {
			return new CodeType(StringUtils.strip(pt.getValue()));
		}
		Coding coding = toCoding(codedElement);
		if (coding != null && !coding.isEmpty()) {
			return new CodeType(coding.getCode());
		}
		return null;
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR Coding
     * @param type The HAPI V2 type to convert
     * @return The Coding converted from the V2 datatype
     */
    public static Coding toCoding(Type type) {
		return toCoding(type, null);
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR Coding
     * @param type The HAPI V2 type to convert
     * @param table The HL7 V2 table or FHIR System to use for the conversion
     * @return The Coding converted from the V2 datatype
     */
    public static Coding toCoding(Type type, String table) {
		CodeableConcept cc = toCodeableConcept(type, table);
		if (cc == null || cc.isEmpty()) {
			return null;
		}
		Coding coding = cc.getCodingFirstRep();
		if (coding == null || coding.isEmpty()) {
			return null;
		}
		if (table != null && !coding.hasSystem()) {
			coding.setSystem(table);
			Mapping.mapSystem(coding);
		}
		return coding;
	}

    /**
     * Convert the Message Code part of a MSG into a Coding
     * @param type	The MSG dataype to convert
     * @return	The coding for the Message Code
     */
	public static Coding toCodingFromMessageCode(Type type) {
		return toCodingFromMSG(type, 0);
	}

    /**
     * Convert the Trigger event part of a MSG into a Coding
     * @param type	The MSG dataype to convert
     * @return	The coding for the Trigger Event
     */
	public static Coding toCodingFromTriggerEvent(Type type) {
		return toCodingFromMSG(type, 1);
	}

    /**
     * Convert the Message Structure part of a MSG into a Coding
     * @param type	The MSG dataype to convert
     * @return	The coding for the Message Structure
     */
	public static Coding toCodingFromMessageStructure(Type type) {
		return toCodingFromMSG(type, 2);
	}

	private static Coding toCodingFromMSG(Type type, int field) {
		String table = null;
		switch (field) {
		case 0:
			table = "0076";
			break;
		case 1:
			table = "0003";
			break;
		case 2:
			table = "0254";
			break;
		default:
			return null;
		}
		if (type instanceof Varies v) {
			type = v.getData();
		}
		if (type instanceof Composite comp) {
			Type[] types = comp.getComponents();
			if (field < types.length) {
				String code = ParserUtils.toString(types[field]);
				if (StringUtils.isNotBlank(code)) {
					return Mapping.mapSystem(new Coding(table, code, Mapping.getDisplay(code, "HL7" + table)));
				}
			}
		}
		return null;
	}
	
	/**
     * Convert a HAPI V2 datatype to a FHIR ContactPoint
     * @param type The HAPI V2 type to convert
     * @return The ContactPoint converted from the V2 datatype
     */
    public static ContactPoint toContactPoint(Type type) {
		return contactPointParser.convert(type);
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR DateTimeType
     * @param type The HAPI V2 type to convert
     * @return The DateTimeType converted from the V2 datatype
     */
    public static DateTimeType toDateTimeType(Type type) {
		InstantType instant = toInstantType(type);
		if (instant == null || instant.isEmpty()) {
			return null;
		}
		return castInto(new DateTimeType(), instant);
	}

    
    /**
	 * Convert between date FHIR types, adjusting as necessary. Basically this
	 * works like a cast.
	 * 
     * @param <T>	The type of the time object to convert from
     * @param <U>	The type of the time object to copy the data to
     * @param to	The time object to convert from
     * @param from	The time object to convert into
     * @return	The converted time object
     */
	public static <T extends BaseDateTimeType, U extends BaseDateTimeType> U castInto(U to, T from) {
		to.setValue(from.getValue());
		to.setPrecision(from.getPrecision());
		return to;
	}

	/**
	 * Convert beween numeric FHIR types, truncating as necessary. Basically this
	 * works like a cast.
	 * 
	 * @param <N1> A Number type (e.g., Long, BigDecimal, Integer, et cetera)
	 * @param <N2> Another Number type
	 * @param <F>  Type type of number used in the from parameter
	 * @param <T>  Type type of number used in the to parameter
	 * @param to   The place to perform the conversion
	 * @param from The place from which to convert
	 * @return The converted type
	 */
	public static <N1 extends Number, N2 extends Number, F extends PrimitiveType<N1>, T extends PrimitiveType<N2>> 
	T castInto(F from, T to) {
		// IntegerType and DecimalType are the only two classes of Number that directly
		// extend PrimitiveType
		if (to instanceof IntegerType i) {
			// PositiveIntType and UnsignedIntType extend IntegerType, so this code works
			// for those as well.
			if (from instanceof DecimalType f) {
				f.round(0, RoundingMode.DOWN); // Truncate to an Integer
				i.setValue(f.getValue().intValueExact());
			}
		} else if (to instanceof DecimalType t) {
			if (from instanceof DecimalType f) {
				t.setValue(f.getValue());
			} else if (from instanceof IntegerType fi) {
				t.setValue(fi.getValue());
			}
		}
		return to;
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR DateType
     * @param type The HAPI V2 type to convert
     * @return The DateType converted from the V2 datatype
     */
    public static DateType toDateType(Type type) {
		InstantType instant = toInstantType(type);
		if (instant == null || instant.isEmpty()) {
			return null;
		}
		return castInto(new DateType(), instant);
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR DecimalType
     * @param pt The HAPI V2 type to convert
     * @return The DecimalType converted from the V2 datatype
     */
    public static DecimalType toDecimalType(Type pt) {
		Quantity qt = toQuantity(pt);
		if (qt == null || qt.isEmpty()) {
			return null;
		}
		return qt.getValueElement();
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR HumanName
     * @param t The HAPI V2 type to convert
     * @return The HumanName converted from the V2 datatype
     */
    public static HumanName toHumanName(Type t) {
		return nameParser.convert(t);
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR Identifier
     * @param t The HAPI V2 type to convert
     * @return The Identifier converted from the V2 datatype
     */
    public static Identifier toIdentifier(Type t) {
		if ((t = adjustIfVaries(t)) == null) {
			return null;
		}

		Identifier id = null;

		if (t instanceof Primitive pt) {
			id = new Identifier().setValue(pt.getValue());
		} else if (t instanceof Composite comp) {
			Type[] types = comp.getComponents();
			if (types.length < 1) {
				return null;
			}
			switch (t.getName()) {
			case "EIP":
				return toIdentifier(types[0]);
			case "HD":
				id = new Identifier();
				setSystemFromHD(id, types, 0);
				break;
			case "EI":
				id = new Identifier();
				id.setValue(DatatypeConverter.getValueOfIdentifier(0, -1, types));
				setSystemFromHD(id, types, 1);
				break;
			case "CE", "CF", "CNE", "CWE":
				Coding coding = toCoding(t);
				if (coding != null) {
					id = new Identifier();
					id.setValue(coding.getCode());
					id.setSystem(coding.getSystem());
				}
				break;
			case "CX":
				id = extractAsIdentifier(comp, 0, 1, 4, 3, 9, 8);
				break;
			case "CNN":
				id = extractAsIdentifier(comp, 0, -1, 7, 9, 8);
				break;
			case "XCN":
				id = extractAsIdentifier(comp, 0, 10, 12, 22, 21, 8);
				break;
			case "XON":
				id = extractAsIdentifier(comp, 0, 9, 3, 6, 8, 6);
				break;
			case "XPN":
			default:
				break;
			}
		}
		if (id == null || id.isEmpty()) {
			return null;
		}
		return id;
	}

	private static void setSystemFromHD(Identifier id, Type[] types, int offset) {
		List<String> s = DatatypeConverter.getSystemsFromHD(offset, types);
		if (!s.isEmpty()) {
			// Last will be a URI or plain string if no URI found.
			id.setSystem(s.get(s.size() - 1));
			// First will be String, which would be a type name.
			String type = s.get(0);
			if (StringUtils.isNotBlank(type)) { // Type is valued, create it in id.
				Coding c = new Coding();
				c.setCode(type);
				if (type.contains(":")) {
					c.setSystem(Systems.IETF); // Type is a URI, so code gets to be IETF
				} else if (Systems.ID_TYPES.contains(type)) {
					c.setSystem(Systems.IDTYPE);
					Mapping.setDisplay(c);
				} else if (Systems.IDENTIFIER_TYPES.contains(type)) {
					c.setSystem(Systems.IDENTIFIER_TYPE);
					Mapping.setDisplay(c);
				}
				id.setType(new CodeableConcept().addCoding(c));
			}
		}
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR IdType
     * @param type The HAPI V2 type to convert
     * @return The IdType converted from the V2 datatype
     */
    public static IdType toIdType(Type type) {
		return new IdType(StringUtils.strip(ParserUtils.toString(type)));
	}

	/**
	 * The HD type is often used to identify the system, and there are two possible
	 * names for the system, one is a local name, and the other is a unique name. The
	 * HD type is often "demoted in place" to replace a string value that identified
	 * an HL7 table name, so can appear as a sequence of 3 components within a data
	 * type at any arbitrary offset.
	 * 
	 * @param offset The offset within the data type
	 * @param types  The list of datatypes to examine
	 * @return A list of possible system names according to the HD found starting at
	 *         offset.
	 */
	private static List<String> getSystemsFromHD(int offset, Type... types) {
		List<String> hdValues = new ArrayList<>();
		if (types.length > offset) {
			String value = ParserUtils.toString(types[offset]);
			if (StringUtils.isNotBlank(value)) {
				hdValues.add(value);
			}
		}
		if (types.length > offset + 1) {
			String prefix = "";
			String value = ParserUtils.toString(types[offset + 1]);
			if (types.length > offset + 2) {
				switch (StringUtils.upperCase(ParserUtils.toString(types[offset]))) {
				case "ISO":
					prefix = "urn:oid:";
					break;
				case "GUID", "UUID":
					prefix = "urn:uuid:";
					break;
				case "URI", "URL":
				default:
					break;
				}
			}
			if (StringUtils.isNotBlank(value)) {
				hdValues.add(prefix + value);
			}
		}
		return hdValues;
	}

	/**
	 * Let HAPI V2 do the parsing work for use in TSComponentOne, which is
	 * independent of any HL7 Version.
	 */
	private static class MyTSComponentOne extends TSComponentOne {
		private static final long serialVersionUID = 1L;

		public MyTSComponentOne() {
			super(null);
		}
	}

	/**
	 * Convert a string to an instant. This method converts a String to an
	 * InstantType. It uses both the HAPI V2 and the FHIR Parsers to attempt to
	 * convert the input. First it tries the HAPI V2 parser using the date without
	 * any ISO Punctuation. If that fails it uses the FHIR Parser. The two parsers
	 * operate differently and have overlapping coverage on their input string
	 * ranges, so this provides the highest level of compatibility.
	 * 
	 * @param value The value to convert
	 * @return An InstantType set to the precision of the timestamp. NOTE: This is a
	 *         small abuse of InstantType.
	 */
	public static InstantType toInstantType(String value) {
		if (value == null || value.length() == 0) {
			return null;
		}
		value = value.strip();
		String original = value; // Save trimmed string for use with FHIR Parser.

		if (value.length() == 0) {
			return null;
		}
		value = removeIsoPunct(value);

		TSComponentOne ts1 = new MyTSComponentOne();
		try {
			ts1.setValue(value);
			String valueWithoutZone = StringUtils.substringBefore(value.replace("+", "-"), "+");
			TemporalPrecisionEnum prec = null;
			String valueWithoutDecimal = StringUtils.substringBefore(value, ".");
			int len = valueWithoutDecimal.length();
			if (len < 5) {
				prec = TemporalPrecisionEnum.YEAR;
			} else if (len < 7) {
				prec = TemporalPrecisionEnum.MONTH;
			} else if (len < 9) {
				prec = TemporalPrecisionEnum.DAY;
			} else if (len < 13) {
				prec = TemporalPrecisionEnum.MINUTE;
			} else if (len < 15) {
				prec = TemporalPrecisionEnum.SECOND;
			}
			Calendar cal = ts1.getValueAsCalendar();
			if (TemporalPrecisionEnum.YEAR.equals(prec)) {
				// Fix V2 Calendar bug when only year is provided.
				cal.set(Calendar.YEAR, Integer.parseInt(valueWithoutDecimal));
				cal.set(Calendar.MONTH, 0);
				cal.set(Calendar.DATE, 1);
			}
			if (valueWithoutDecimal.length() < valueWithoutZone.length()) {
				prec = TemporalPrecisionEnum.MILLI;
			}
			InstantType t = new InstantType(cal);
			t.setPrecision(prec);
			return t;
		} catch (Exception e) {
			try {
				// We failed to convert, try as FHIR
				BaseDateTimeType fhirType = new DateTimeType();
				fhirType.setValueAsString(original);
				Date date = fhirType.getValue();
				TemporalPrecisionEnum prec = fhirType.getPrecision();
				InstantType instant = new InstantType();
				TimeZone tz = fhirType.getTimeZone();
				instant.setValue(date);
				instant.setPrecision(prec);
				instant.setTimeZone(tz);
				return instant;
			} catch (Exception ex) {
				debugException("Unexpected FHIR {} parsing {} as InstantType: {}", e.getClass().getSimpleName(),
						original, ex.getMessage(), ex);
			}
			debugException("Unexpected   V2 {} parsing {} as InstantType: {}", e.getClass().getSimpleName(), original,
					e.getMessage());
			return null;
		}
	}

	/** 
	 * Remove punctuation characters from an ISO-8601 date or datetime type 
	 * @param value	The string to remove characters from
	 * @return	The ISO-8601 string without punctuation.
	 */
	public static String removeIsoPunct(String value) {
		if (value == null) {
			return null;
		}
		value = value.toUpperCase();
		String left = value.substring(0, Math.min(11, value.length()));
		String right = value.length() == left.length() ? "" : value.substring(left.length());
		left = left.replace("-", "").replace("T", ""); // Remove - and T from date part
		right = right.replace("-", "+"); // Change any - to +, and remove :
		String tz = StringUtils.substringAfter(right, "+"); // Get TZ length after +
		right = StringUtils.substringBefore(right, "+");
		if (tz.length() != 0) {
			tz = value.substring(value.length() - tz.length() - 1); // adjust TZ
		} else if (right.endsWith("Z")) { // Check for ZULU time
			right = right.substring(0, right.length() - 1);
			tz = "Z";
		}
		right = right.replace(":", "");
		value = left + right + tz;
		return value;
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR InstantType
     * @param type The HAPI V2 type to convert
     * @return The InstantType converted from the V2 datatype
     */
    public static InstantType toInstantType(Type type) {
		// This will convert the first primitive component of anything to an instant.
		if (type instanceof TSComponentOne ts1) {
			try {
				Date date = ts1.getValueAsDate();
				if (date != null) {
					InstantType instant = new InstantType();
					TemporalPrecisionEnum prec = getTemporalPrecision(ts1);
					instant.setValue(date);
					instant.setPrecision(prec);
					return instant;
				}
				return null;
			} catch (DataTypeException e) {
				warn("Unexpected {} parsing {} as InstantType: {}", e.getClass().getSimpleName(), type,
						e.getMessage());
			}
		}
		return toInstantType(ParserUtils.toString(type));
	}

	private static TemporalPrecisionEnum getTemporalPrecision(TSComponentOne ts1) {
		String v = ts1.getValue();
		String ts = StringUtils.substringBefore(v, ".");
		if (ts.length() != v.length()) {
			return TemporalPrecisionEnum.MILLI;
		}
		StringUtils.replace(ts, "-", "+");
		ts = StringUtils.substringBefore(ts1.getValue(), "+");
		switch (ts.length()) {
		case 1, 2, 3, 4:
			return TemporalPrecisionEnum.YEAR;
		case 5, 6:
			return TemporalPrecisionEnum.MONTH;
		case 7, 8:
			return TemporalPrecisionEnum.DAY;
		case 9, 10, 11, 12:
			return TemporalPrecisionEnum.MINUTE;
		case 13, 14:
			return TemporalPrecisionEnum.SECOND;
		default:
			return TemporalPrecisionEnum.MILLI;
		}
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR IntegerType
     * @param pt The HAPI V2 type to convert
     * @return The IntegerType converted from the V2 datatype
     */
    public static IntegerType toIntegerType(Type pt) {
		DecimalType dt = toDecimalType(pt);
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		BigDecimal decimal = dt.getValue();
		BigInteger bigInt = decimal.toBigInteger();
		try {
			int value = bigInt.intValueExact();
			IntegerType i = new IntegerType(value);
			i.setValue(i.getValue()); // Force normalization of string value
			return i;
		} catch (ArithmeticException ex) {
			warn("Integer overflow value in field {}", pt.toString());
			return null;
		}
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR PositiveIntType
     * @param pt The HAPI V2 type to convert
     * @return The PositiveIntType converted from the V2 datatype
     */
    public static PositiveIntType toPositiveIntType(Type pt) {
		IntegerType dt = toIntegerType(pt);
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		if (dt.getValue() < 0) {
			warn("Illegal negative value in field {}", pt.toString());
			return null;
		}
		return new PositiveIntType(dt.getValue());
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR Quantity
     * @param type The HAPI V2 type to convert
     * @return The Quantity converted from the V2 datatype
     */
    public static Quantity toQuantity(Type type) {
		Quantity qt = null;
		if ((type = adjustIfVaries(type)) == null) {
			return null;
		}
		
		if (type instanceof Primitive pt) {
			qt = getQuantity(pt);
		}
		if (type instanceof Composite comp) {
			Type[] types = comp.getComponents();
			if ("CQ".equals(type.getName()) // NOSONAR name check is OK here
					&& types.length > 0) { // NOSONAR name compare is correct
				qt = getQuantity((Primitive) types[0]);
				if (types.length > 1) {
					if (qt == null) {
						qt = new Quantity();
					}
					setUnits(qt, types[1]);
				}
			}
		}

		if (qt == null || qt.isEmpty()) {
			return null;
		}
		return qt;
	}

    /**
     * Convert a HAPI V2 datatype used for length of stay into a FHIR Quantity 
     * @param pt	The type to convert
     * @return	The converted Quantity
     */
	public static Quantity toQuantityLengthOfStay(Type pt) {
		Quantity qt = toQuantity(pt);
		if (qt == null || qt.isEmpty()) {
			return null;
		}
		qt.setCode("d");
		qt.setUnit("days");
		qt.setSystem(Systems.UCUM);
		return qt;
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR StringType
     * @param type The HAPI V2 type to convert
     * @return The StringType converted from the V2 datatype
     */
    public static StringType toStringType(Type type) {
		if (type == null) {
			return null;
		}
		type = adjustIfVaries(type);
		if (type instanceof Primitive pt) {
			return new StringType(pt.getValue());
		}
		String s = null;
		switch (type.getName()) {
		case "CE", "CF", "CNE", "CWE":
			CodeableConcept cc = toCodeableConcept(type);
			s = toString(cc);
			break;
		case "ERL":
			// TODO: Consider converting this type to a FHIRPath expression
			try {
				s = type.encode();
			} catch (HL7Exception e) {
				// ignore this error.
			}
			break;
		case "CQ":
			Quantity qt = toQuantity(type);
			s = toString(qt);
			break;
		default:
			break;
		}
		if (s == null) {
			return null;
		}
		return new StringType(s);
	}

	private static final String V2_TIME_FORMAT = "HHmmss.SSSS";
	private static final String FHIR_TIME_FORMAT = "HH:mm:ss.SSS";

	/**
	 * Convert a string to a FHIR TimeType object.
	 * 
	 * NOTE: V2 allows times to specify a time zone, FHIR does not, but HAPI FHIR TimeType is
	 * very forgiving in this respect, as it does not structure TimeType into parts.
	 * 
	 * @param value	The string representing the time
	 * @return	A FHIR TimeType object representing that string
	 */
	public static TimeType toTimeType(String value) {

		if (StringUtils.isBlank(value)) {
			return null;
		}
		value = value.replace(":", "").replace(" ", "");
		if (!value.matches(
			"^\\d{2}"
			+ "("
				+ "\\d{2}"
				+ "("
					+ "\\.\\d{1,4}"
				+ ")?"
			+ ")?"
			+ "("
				+ "\\[\\-+]\\d{2}"
				+ "("
					+ "\\d{2}"
				+ ")?"
			+ ")?$"
		)) {
			warn("Value does not match date pattern for V2 HH[MM[SS[.S[S[S[S]]]]]][+/-ZZZZ]");
			return null;
		}
		// Parse according to V2 rule: HH[MM[SS[.S[S[S[S]]]]]][+/-ZZZZ]
		// Remove any inserted : or space values.
		String timePart = value.split("[\\-+]")[0];
		String zonePart = StringUtils.right(value, value.length() - (timePart.length() + 1));
		String wholePart = StringUtils.substringBefore(timePart, ".");
		try {
			if (!checkTime(wholePart, "time")) {
				return null;
			}
			if (zonePart.length() == 0 || !checkTime(zonePart, "timezone")) {
				return null;
			}
		} catch (NumberFormatException ex) {
			warn("Not a valid time {}", value);
			return null;
		}
		boolean hasTz = timePart.length() < value.length();
		String fmt = StringUtils.left(V2_TIME_FORMAT, timePart.length());
		if (hasTz) {
			fmt += "ZZZZ"; // It has a time zone
		}
		FastDateFormat ft = FastDateFormat.getInstance(fmt);

		try {
			Date d = ft.parse(value);
			TimeType t = new TimeType();
			if (value.contains(".")) {
				fmt = FHIR_TIME_FORMAT;
			} else
				switch (timePart.length()) {
				case 1, 2:
					fmt = StringUtils.left(FHIR_TIME_FORMAT, 2);
					break;
				case 3, 4:
					fmt = StringUtils.left(FHIR_TIME_FORMAT, 5);
					break;
				default:
					fmt = StringUtils.left(FHIR_TIME_FORMAT, 8);
					break;
				}
			if (hasTz) {
				fmt += "ZZZZ"; // It has a time zone
			}
			ft = FastDateFormat.getInstance(fmt);
			t.setValue(ft.format(d));
			return t;
		} catch (ParseException e) {
			warn("Error parsing time {}", value);
			return null;
		}
	}

	private static boolean checkTime(String wholePart, String where) {
		String[] parts = { "hour", "minute", "second" };
		int i;
		for (i = 0; i < wholePart.length() && i < 6; i += 2) {
			String part = StringUtils.substring(wholePart, i, i + 2);
			if (part.length() != 2) {
				warn("Missing {} digits in {} of {} ", parts[i / 2], where, wholePart);
				return false;
			}
			int v = Integer.parseInt(part);
			if ((i == 0 && v > 23) || (v > 60)) {
				warn("Invalid {} in {} of {}", parts[i / 2], where, wholePart);
				return false;
			}
		}
		if (i < 2) {
			warn("Missing hours in {} of {}", wholePart, where);
			return false;
		}
		return i > 0;
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR TimeType
     * @param type The HAPI V2 type to convert
     * @return The TimeType converted from the V2 datatype
     */
    public static TimeType toTimeType(Type type) {
		// This will convert the first primitive component of anything to a time.
		return toTimeType(ParserUtils.toString(type));
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR UnsignedIntType
     * @param pt The HAPI V2 type to convert
     * @return The UnsignedIntType converted from the V2 datatype
     */
    public static UnsignedIntType toUnsignedIntType(Type pt) {
		DecimalType dt = toDecimalType(pt);
		if (dt == null || dt.isEmpty()) {
			return null;
		}
		if (dt.getValue().compareTo(BigDecimal.ZERO) < 0) {
			warn("Illegal negative value in field {}", pt.toString(), pt);
			return null;
		}
		if (dt.getValue().compareTo(MAX_UNSIGNED_VALUE) > 0) {
			warn("Unsigned Integer overflow value in field {}", pt.toString(), pt);
			return null;
		}
		return new UnsignedIntType(dt.getValueAsInteger());
	}

	/**
     * Convert a HAPI V2 datatype to a FHIR UriType
     * @param type The HAPI V2 type to convert
     * @return The UriType converted from the V2 datatype
     */
    public static UriType toUriType(Type type) {
		if (type == null) {
			return null;
		}
		type = adjustIfVaries(type);
		if (type instanceof Primitive pt) {
			return new UriType(StringUtils.strip(pt.getValue()));
		}
		Type[] types = ((Composite) type).getComponents();
		if (types.length == 0) {
			return null;
		}
		if ("HD".equals(type.getName())) { // NOSONAR Name
											// comparison is
											// correct
			Identifier id = toIdentifier(type);
			if (id != null && !id.isEmpty()) {
				return new UriType(id.getValue());
			}
		}
		return null;
	}

	private static int compareUnitsBySystem(Coding c1, Coding c2) {
		if (Systems.UCUM.equals(c1.getSystem())) {
			return -1;
		} else if (Systems.UCUM.equals(c2.getSystem())) {
			return 1;
		}
		return StringUtils.compare(c1.getSystem(), c2.getSystem());
	}

	private static Coding getCoding(Composite composite, int index, boolean hasDisplay) {
		Type[] types = composite.getComponents();
		int versionIndex = 6;
		int codeSystemOID = 13;
		if ("EI".equals(composite.getName())) { // NOSONAR Use of string comparison is correct
			versionIndex = 99;
			codeSystemOID = 2;
			hasDisplay = false;
		} else if (index == 3) {
			versionIndex = 7;
			codeSystemOID = 16;
		} else if (index == 9) {
			versionIndex = 12;
			codeSystemOID = 20;
		}
		try {
			Coding coding = new Coding();
			if (index >= types.length) {
				return null;
			}
			setValue(coding::setCode, types, index++);
			if (hasDisplay) {
				setValue(coding::setDisplay, types, index++);
			}
			setValue(coding::setSystem, types, index++);
			setValue(coding::setVersion, types, versionIndex);
			setValue(coding::setSystem, types, codeSystemOID);

			Mapping.mapSystem(coding);
			if (!coding.hasDisplay() || coding.getDisplay().equals(coding.getCode())) {
				// See if we can do better for display names
				Mapping.setDisplay(coding);
			}
			return coding;
		} catch (Exception e) {
			warnException("Unexpected {} converting {}[{}] to Coding: {}", e.getClass().getName(), composite.toString(),
					index, e.getMessage(), e);
			return null;
		}
	}

	private static Quantity getQuantity(Primitive pt) {
		Quantity qt = new Quantity();
		String value = null;
		if (StringUtils.isBlank(pt.getValue())) {
			return null;
		}
		value = pt.getValue().strip();
		String[] valueParts = value.split("\\s+");
		try {
			qt.setValueElement(new DecimalType(valueParts[0]));
		} catch (NumberFormatException ex) {
			return null;
		}
		if (valueParts.length > 1) {
			Coding coding = Units.toUcum(valueParts[1]);
			if (coding != null) {
				qt.setCode(coding.getCode());
				qt.setUnit(coding.getDisplay());
				qt.setSystem(coding.getSystem());
			}
		}
		if (qt.isEmpty()) {
			return null;
		}
		return qt;
	}

	private static void setUnits(Quantity qt, Type unit) {
		CodeableConcept cc = toCodeableConcept(unit);
		if (cc != null && cc.hasCoding()) {
			List<Coding> codingList = cc.getCoding();
			Collections.sort(codingList, DatatypeConverter::compareUnitsBySystem);
			Coding coding = codingList.get(0);
			qt.setCode(coding.getCode());
			qt.setSystem(Systems.UCUM);
			qt.setUnit(coding.getDisplay());
		}
	}

	private static void setValue(Consumer<String> consumer, Type[] types, int i) {
		Type type = adjustIfVaries(types, i);
		if (type instanceof Primitive st) {
			String value = ParserUtils.toString(st);
			if (StringUtils.isNotEmpty(value)) {
				consumer.accept(st.getValue());
			}
		}
	}

	private static String toString(CodeableConcept cc) {
		if (cc == null) {
			return null;
		}
		if (cc.hasText()) {
			return cc.getText();
		}
		if (cc.hasCoding()) {
			Coding coding = cc.getCodingFirstRep();
			if (coding.hasDisplay()) {
				return coding.getDisplay();
			}
			if (coding.hasCode()) {
				return coding.getCode();
			}
		}
		return null;
	}

	private static String toString(Quantity qt) {
		if (qt == null) {
			return null;
		}
		StringBuilder b = new StringBuilder();
		if (qt.hasValue()) {
			b.append(qt.getValue().toString());
		}
		if (qt.hasUnit()) {
			b.append(' ');
			b.append(qt.getUnit());
		}
		if (StringUtils.isBlank(b)) {
			return null;
		}
		return b.toString();
	}

	private static void warn(String msg, Object... args) {
		log.warn(msg, args);
	}

	private static void warnException(String msg, Object... args) {
		log.error(msg, args);
	}

	private static void debugException(String msg, Object... args) {
		log.debug(msg, args);
	}
}
