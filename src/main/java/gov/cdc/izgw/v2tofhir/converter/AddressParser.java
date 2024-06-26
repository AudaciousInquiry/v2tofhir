package gov.cdc.izgw.v2tofhir.converter;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressUse;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddressParser {

	private static final Pattern caPostalCode1 = Pattern
			.compile("^[a-zA-Z]\\d[a-zA-Z]$");

	private static final Pattern caPostalCode2 = Pattern
			.compile("^\\d[a-zA-Z]\\d$");

	private static final Pattern countryPattern = ParserUtils.toPattern("US",
			"U.S.", "U.S.A.", "USA", "United States",
			"United States of America", "CN", "Canada", "MX", "Mexico", "BZ",
			"BELIZE", "CR", "COSTA RICA", "CZ", "CANAL ZONE", "SV",
			"EL SALVADOR", "GT", "GUATEMALA", "HN", "HONDURAS", "NI",
			"NICARAGUA", "PA", "PANAMA");

	private static final Pattern postalCodePattern = Pattern.compile(
			"^\\d{5}$|^\\d{5}-\\d{4}$|^[a-zA-Z]\\d[a-zA-Z]-\\d[a-zA-Z]\\d$");

	private static final Pattern statePattern = ParserUtils.toPattern("AL",
			"Alabama", "AK", "Alaska", "AZ", "Arizona", "AR", "Arkansas", "AS",
			"American Samoa", "CA", "California", "CO", "Colorado", "CT",
			"Connecticut", "DE", "Delaware", "DC", "District of Columbia", "FL",
			"Florida", "GA", "Georgia", "GU", "Guam", "HI", "Hawaii", "ID",
			"Idaho", "IL", "Illinois", "IN", "Indiana", "IA", "Iowa", "KS",
			"Kansas", "KY", "Kentucky", "LA", "Louisiana", "ME", "Maine", "MD",
			"Maryland", "MA", "Massachusetts", "MI", "Michigan", "MN",
			"Minnesota", "MS", "Mississippi", "MO", "Missouri", "MT", "Montana",
			"NE", "Nebraska", "NV", "Nevada", "NH", "New Hampshire", "NJ",
			"New Jersey", "NM", "New Mexico", "NY", "New York", "NC",
			"North Carolina", "ND", "North Dakota", "MP",
			"Northern Mariana Islands", "OH", "Ohio", "OK", "Oklahoma", "OR",
			"Oregon", "PA", "Pennsylvania", "PR", "Puerto Rico", "RI",
			"Rhode Island", "SC", "South Carolina", "SD", "South Dakota", "TN",
			"Tennessee", "TX", "Texas", "TT", "Trust Territories", "UT", "Utah",
			"VT", "Vermont", "VA", "Virginia", "VI", "Virgin Islands", "WA",
			"Washington", "WV", "West Virginia", "WI", "Wisconsin", "WY",
			"Wyoming", "Newfoundland and Labrador", "Newfoundland", "Labrador",
			"NL", "Prince Edward Island", "PE", "Nova Scotia", "NS",
			"New Brunswick", "NB", "Quebec", "QC", "Ontario", "ON", "Manitoba",
			"MB", "Saskatchewan", "SK", "Alberta", "AB", "British Columbia",
			"BC", "Yukon", "YT", "Northwest Territories", "NT", "Nunavut", "NU",
			"AG", "AGUASCALIENTES", "BN", "BAJA CALIFORNIA NORTE", "BS",
			"BAJA CALIFORNIA SUR", "CH", "COAHUILA", "CI", "CHIHUAHUA", "CL",
			"COLIMA", "CP", "CAMPECHE", "CS", "CHIAPAS", "DF",
			"DISTRICTO FEDERAL", "DG", "DURANGO", "GE", "GUERRERO", "GJ",
			"GUANAJUATO", "HD", "HIDALGO", "JA", "JALISCO", "MC", "MICHOACAN",
			"MR", "MORELOS", "MX", "MEXICO", "NA", "NAYARIT", "NL",
			"NUEVO LEON", "OA", "OAXACA", "PU", "PUEBLA", "QE", "QUERETARO",
			"QI", "QUINTANA ROO", "SI", "SINALOA", "SL", "SAN LUIS POTOSI",
			"SO", "SONORA", "TA", "TAMAULIPAS", "TB", "TABASCO", "TL",
			"TLAXCALA", "VC", "VERACRUZ", "YU", "YUCATAN", "ZA", "ZACATECA");
	// See https://pe.usps.com/text/pub28/28apc_002.htm
	private static final Pattern streetPattern = ParserUtils.toPattern("ALLEE",
			"ALLEY", "ALLY", "ALY", "ANEX", "ANNEX", "ANNX", "ANX", "ARC",
			"ARCADE", "AV", "AVE", "AVEN", "AVENU", "AVENUE", "AVN", "AVNUE",
			"BAYOO", "BAYOU", "BCH", "BEACH", "BEND", "BG", "BGS", "BLF",
			"BLFS", "BLUF", "BLUFF", "BLUFFS", "BLVD", "BND", "BOT", "BOTTM",
			"BOTTOM", "BOUL", "BOULEVARD", "BOULV", "BR", "BRANCH", "BRDGE",
			"BRG", "BRIDGE", "BRK", "BRKS", "BRNCH", "BROOK", "BROOKS", "BTM",
			"BURG", "BURGS", "BYP", "BYPA", "BYPAS", "BYPASS", "BYPS", "BYU",
			"CAMP", "CANYN", "CANYON", "CAPE", "CAUSEWAY", "CAUSWA", "CEN",
			"CENT", "CENTER", "CENTERS", "CENTR", "CENTRE", "CIR", "CIRC",
			"CIRCL", "CIRCLE", "CIRCLES", "CIRS", "CLB", "CLF", "CLFS", "CLIFF",
			"CLIFFS", "CLUB", "CMN", "CMNS", "CMP", "CNTER", "CNTR", "CNYN",
			"COMMON", "COMMONS", "COR", "CORNER", "CORNERS", "CORS", "COURSE",
			"COURT", "COURTS", "COVE", "COVES", "CP", "CPE", "CRCL", "CRCLE",
			"CREEK", "CRES", "CRESCENT", "CREST", "CRK", "CROSSING",
			"CROSSROAD", "CROSSROADS", "CRSE", "CRSENT", "CRSNT", "CRSSNG",
			"CRST", "CSWY", "CT", "CTR", "CTRS", "CTS", "CURV", "CURVE", "CV",
			"CVS", "CYN", "DALE", "DAM", "DIV", "DIVIDE", "DL", "DM", "DR",
			"DRIV", "DRIVE", "DRIVES", "DRS", "DRV", "DV", "DVD", "EST",
			"ESTATE", "ESTATES", "ESTS", "EXP", "EXPR", "EXPRESS", "EXPRESSWAY",
			"EXPW", "EXPY", "EXT", "EXTENSION", "EXTENSIONS", "EXTN", "EXTNSN",
			"EXTS", "FALL", "FALLS", "FERRY", "FIELD", "FIELDS", "FLAT",
			"FLATS", "FLD", "FLDS", "FLS", "FLT", "FLTS", "FORD", "FORDS",
			"FOREST", "FORESTS", "FORG", "FORGE", "FORGES", "FORK", "FORKS",
			"FORT", "FRD", "FRDS", "FREEWAY", "FREEWY", "FRG", "FRGS", "FRK",
			"FRKS", "FRRY", "FRST", "FRT", "FRWAY", "FRWY", "FRY", "FT", "FWY",
			"GARDEN", "GARDENS", "GARDN", "GATEWAY", "GATEWY", "GATWAY", "GDN",
			"GDNS", "GLEN", "GLENS", "GLN", "GLNS", "GRDEN", "GRDN", "GRDNS",
			"GREEN", "GREENS", "GRN", "GRNS", "GROV", "GROVE", "GROVES", "GRV",
			"GRVS", "GTWAY", "GTWY", "HARB", "HARBOR", "HARBORS", "HARBR",
			"HAVEN", "HBR", "HBRS", "HEIGHTS", "HIGHWAY", "HIGHWY", "HILL",
			"HILLS", "HIWAY", "HIWY", "HL", "HLLW", "HLS", "HOLLOW", "HOLLOWS",
			"HOLW", "HOLWS", "HRBOR", "HT", "HTS", "HVN", "HWAY", "HWY",
			"INLET", "INLT", "IS", "ISLAND", "ISLANDS", "ISLE", "ISLES",
			"ISLND", "ISLNDS", "ISS", "JCT", "JCTION", "JCTN", "JCTNS", "JCTS",
			"JUNCTION", "JUNCTIONS", "JUNCTN", "JUNCTON", "KEY", "KEYS", "KNL",
			"KNLS", "KNOL", "KNOLL", "KNOLLS", "KY", "KYS", "LAKE", "LAKES",
			"LAND", "LANDING", "LANE", "LCK", "LCKS", "LDG", "LDGE", "LF",
			"LGT", "LGTS", "LIGHT", "LIGHTS", "LK", "LKS", "LN", "LNDG",
			"LNDNG", "LOAF", "LOCK", "LOCKS", "LODG", "LODGE", "LOOP", "LOOPS",
			"MALL", "MANOR", "MANORS", "MDW", "MDWS", "MEADOW", "MEADOWS",
			"MEDOWS", "MEWS", "MILL", "MILLS", "MISSION", "MISSN", "ML", "MLS",
			"MNR", "MNRS", "MNT", "MNTAIN", "MNTN", "MNTNS", "MOTORWAY",
			"MOUNT", "MOUNTAIN", "MOUNTAINS", "MOUNTIN", "MSN", "MSSN", "MT",
			"MTIN", "MTN", "MTNS", "MTWY", "NCK", "NECK", "OPAS", "ORCH",
			"ORCHARD", "ORCHRD", "OVAL", "OVERPASS", "OVL", "PARK", "PARKS",
			"PARKWAY", "PARKWAYS", "PARKWY", "PASS", "PASSAGE", "PATH", "PATHS",
			"PIKE", "PIKES", "PINE", "PINES", "PKWAY", "PKWY", "PKWYS", "PKY",
			"PL", "PLACE", "PLAIN", "PLAINS", "PLAZA", "PLN", "PLNS", "PLZ",
			"PLZA", "PNE", "PNES", "POINT", "POINTS", "PORT", "PORTS", "PR",
			"PRAIRIE", "PRK", "PRR", "PRT", "PRTS", "PSGE", "PT", "PTS", "RAD",
			"RADIAL", "RADIEL", "RADL", "RAMP", "RANCH", "RANCHES", "RAPID",
			"RAPIDS", "RD", "RDG", "RDGE", "RDGS", "RDS", "REST", "RIDGE",
			"RIDGES", "RIV", "RIVER", "RIVR", "RNCH", "RNCHS", "ROAD", "ROADS",
			"ROUTE", "ROW", "RPD", "RPDS", "RST", "RTE", "RUE", "RUN", "RVR",
			"SHL", "SHLS", "SHOAL", "SHOALS", "SHOAR", "SHOARS", "SHORE",
			"SHORES", "SHR", "SHRS", "SKWY", "SKYWAY", "SMT", "SPG", "SPGS",
			"SPNG", "SPNGS", "SPRING", "SPRINGS", "SPRNG", "SPRNGS", "SPUR",
			"SPURS", "SQ", "SQR", "SQRE", "SQRS", "SQS", "SQU", "SQUARE",
			"SQUARES", "ST", "STA", "STATION", "STATN", "STN", "STR", "STRA",
			"STRAV", "STRAVEN", "STRAVENUE", "STRAVN", "STREAM", "STREET",
			"STREETS", "STREME", "STRM", "STRT", "STRVN", "STRVNUE", "STS",
			"SUMIT", "SUMITT", "SUMMIT", "TER", "TERR", "TERRACE", "THROUGHWAY",
			"TPKE", "TRACE", "TRACES", "TRACK", "TRACKS", "TRAFFICWAY", "TRAIL",
			"TRAILER", "TRAILS", "TRAK", "TRCE", "TRFY", "TRK", "TRKS", "TRL",
			"TRLR", "TRLRS", "TRLS", "TRNPK", "TRWY", "TUNEL", "TUNL", "TUNLS",
			"TUNNEL", "TUNNELS", "TUNNL", "TURNPIKE", "TURNPK", "UN",
			"UNDERPASS", "UNION", "UNIONS", "UNS", "UPAS", "VALLEY", "VALLEYS",
			"VALLY", "VDCT", "VIA", "VIADCT", "VIADUCT", "VIEW", "VIEWS",
			"VILL", "VILLAG", "VILLAGE", "VILLAGES", "VILLE", "VILLG",
			"VILLIAGE", "VIS", "VIST", "VISTA", "VL", "VLG", "VLGS", "VLLY",
			"VLY", "VLYS", "VST", "VSTA", "VW", "VWS", "WALK", "WALKS", "WALL",
			"WAY", "WAYS", "WELL", "WELLS", "WL", "WLS", "WY", "XING", "XRD",
			"XRDS", "AVENIDA", "CALLE", "CLL", "CAMINITO", "CMT", "CAMINO",
			"CAM", "CERRADA", "CER", "CIRCULO", "ENTRADA", "ENT", "PASEO",
			"PSO", "PLACITA", "PLA", "RANCHO", "RCH", "VEREDA", "VER", "VIS");

	// See https://pe.usps.com/text/pub28/pub28apc_003.htm
	private static final Pattern unitPattern = ParserUtils.toPattern(
			"Apartment", "APT", "Basement", "BLDG", "BOX", "BSMT", "Building",
			"Department", "DEPT", "FL", "Floor", "FRNT", "Front", "Hanger",
			"HNGR", "KEY", "Key", "LBBY", "Lobby", "LOT", "Lot", "Lower",
			"LOWR", "OFC", "Office", "Penthouse", "PH", "PIER", "Pier", "REAR",
			"Rear", "RM", "Room", "SIDE", "Side", "SLIP", "Slip", "Space",
			"SPC", "STE", "STOP", "Stop", "Suite", "Trailer", "TRLR", "UNIT",
			"Unit", "Upper", "UPPR", "Altura", "ALT", "Alturas", "ALTS",
			"Barriada", "BDA", "Barrio", "BO", "Carretera", "CARR",
			"Condominio", "COND", "Cooperativa", "COO", "Departamento", "DEPT",
			"Edificio", "EDIF", "Estancias", "EST", "Extensión", "EXT",
			"Industrial Interior", "IND INT", "Jardines", "JARD", "Mansiones",
			"MANS", "Parcelas", "PARC", "Quebrada", "QBDA", "Reparto", "REPTO",
			"Residencial", "RES", "Sector", "SECT", "Sección", "SECC",
			"Terraza", "TERR", "Urbanización", "URB");

	private static final Pattern directionalPattern = ParserUtils.toPattern("N",
			"NORTE", "NORTH", "NE", "NORESTE", "NORTHEAST", "NW", "NOROESTE",
			"NORTHWEST", "S", "SUR", "SOUTH", "SE", "SURESTE", "SOUTHEAST",
			"SW", "SUROESTE", "SOUTHWEST", "E", "ESTE", "EAST", "W", "OESTE",
			"WEST");

	public static Address toAddress(Type type) {
		Address addr = null;
		if (type instanceof Primitive pt) {
			addr = parse(pt.getValue());
		} else if (type instanceof Composite comp && Arrays.asList("AD", "XAD")
				.contains(type.getName())) {
			addr = parse(comp.getComponents());
		}
		if (addr == null || addr.isEmpty()) {
			return null;
		}
		return addr;
	}
	public static Address parse(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		value = StringUtils.normalizeSpace(value);
		String[] parts = value.split("[\\n\\r]");
		if (parts.length == 1) {
			parts = value.split(",");
		}
		Address addr = new Address();
		addr.setText(value);
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();
			if (part.isEmpty()) {
				continue;
			}
			if (!addr.hasLine()) {
				// First line is usually an address line
				addr.addLine(part);
			} else if (streetPattern.matcher(part).matches()
					|| directionalPattern.matcher(part).matches()
					|| unitPattern.matcher(part).matches()) {
				addr.addLine(part);
			} else if (!addr.hasCountry()
					&& countryPattern.matcher(part).matches()) {
				addr.setCountry(part);
			} else if (!addr.hasPostalCode()) {
				getCityStatePostalCode(addr, part);
			}
		}
		return null;
	}
	private static void getCityStatePostalCode(Address addr, String part) {
		// could be an address line, country, or some combination of
		// city, state, and postal code

		String[] lineParts = part.split("[, ]+");
		int position = getPostalCode(addr, lineParts);
		if (addr.hasPostalCode()) {
			// Remove the postal code from the line wherever it is.
			lineParts = ParserUtils.removeArrayElement(lineParts, position);
		}
		position = getState(addr, lineParts);
		if (addr.hasState()) {
			lineParts = ParserUtils.removeArrayElement(lineParts, position);
		}
		// If there was a postal code or state, city is anything lef.
		if (addr.hasPostalCode() || addr.hasState()) {
			addr.setCity(StringUtils.join(" ", lineParts));
		}
	}
	private static int getState(Address addr, String[] lineParts) {
		int position;
		position = 0;
		for (String linePart : lineParts) {
			++position;
			if (statePattern.matcher(linePart).matches()) {
				addr.setState(linePart);
				break;
			}
		}
		return position;
	}
	private static int getPostalCode(Address addr, String[] lineParts) {
		String postalPart1 = null;
		int position = 0;
		for (String linePart : lineParts) {
			++position;
			if (postalCodePattern.matcher(linePart).matches()) {
				addr.setPostalCode(linePart);
				break;
			} else if (postalPart1 == null
					&& caPostalCode1.matcher(linePart).matches()) {
				postalPart1 = linePart;
			} else if (postalPart1 != null
					&& caPostalCode2.matcher(linePart).matches()) {
				addr.setPostalCode(postalPart1 + " " + linePart);
				break;
			} else {
				postalPart1 = null;
			}
		}
		return position;
	}
	
	public static Address parse(Type[] types) {
		int offset = 0;
		Address addr = new Address();
		for (int i = 0; i < 14; i++) {
			if (i == 0) {
				addr.addLine(ParserUtils.toString(types[i + offset]));
			} else if (types[i + offset] instanceof Primitive part) {
				switch (i) {
					case 1 :
						addr.addLine(part.getValue());
						break;
					case 2 :
						addr.setCity(part.getValue());
						break;
					case 3 :
						addr.setState(part.getValue());
						break;
					case 4 :
						addr.setPostalCode(part.getValue());
						break;
					case 5 :
						addr.setCountry(part.getValue());
						break;
					case 6 :
						addr.setUse(getUse(part.getValue()));
						break;
					case 8 :
						addr.setDistrict(part.getValue());
						break;
					default:
						break;
					}
			}
		}
		if (addr.isEmpty()) {
			return null;
		}
		return addr;
	}
	private static AddressUse getUse(String value) {
		switch (value.trim().toUpperCase()) {
			case "B", // Firm/Business
				 "O" : // Office/Business
				return AddressUse.WORK;
			case "BI" : // Billing Address
				return AddressUse.BILLING;
			case "C" : // Current Or Temporary
				return AddressUse.TEMP;
			case "H" : // Home
				return AddressUse.HOME;
			case "BA", // Bad address
				 "BDL", // Birth delivery location (address where birth occurred)
				 "BR", // Residence at birth (home address at time of birth)
				 "F", // Country Of Origin
				 "L", // Legal Address
				 "M", // Mailing
				 "N", // Birth (nee) (birth address, not otherwise specified)
				 "P", // Permanent
				 "RH", // Registry home
				 "S", // Service Location
				 "SH", // Shipping Address
				 "TM", // Tube Address
				 "V" : // Vacation
			default :
				return null;
		}
	}
	private AddressParser() {
	}

}
