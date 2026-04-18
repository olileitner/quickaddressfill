package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Detects address/tag conflicts between existing building tags and the values selected for apply,
 * including city/country-aware overwrite detection.
 */
final class AddressConflictService {

	/**
	 * One differing tag field between existing and proposed address/building values.
	 */
	static final class ConflictField {
		private final String key;
		private final String existingValue;
		private final String proposedValue;

		ConflictField(String key, String existingValue, String proposedValue) {
			this.key = key;
			this.existingValue = normalize(existingValue);
			this.proposedValue = normalize(proposedValue);
		}

		String getKey() {
			return key;
		}

		String getExistingValue() {
			return existingValue;
		}

		String getProposedValue() {
			return proposedValue;
		}
	}

	/**
	 * Structured outcome of conflict detection used by overwrite-warning UI.
	 */
	static final class ConflictAnalysis {
		private final boolean hasConflict;
		private final String overwrittenStreet;
		private final String overwrittenPostcode;
		private final String overwrittenCity;
		private final String overwrittenCountry;
		private final List<ConflictField> differingFields;

		ConflictAnalysis(boolean hasConflict, String overwrittenStreet, List<ConflictField> differingFields) {
			this(hasConflict, overwrittenStreet, "", "", differingFields);
		}

		ConflictAnalysis(boolean hasConflict, String overwrittenStreet, String overwrittenPostcode, List<ConflictField> differingFields) {
			this(hasConflict, overwrittenStreet, overwrittenPostcode, "", differingFields);
		}

		ConflictAnalysis(boolean hasConflict, String overwrittenStreet, String overwrittenPostcode,
				String overwrittenCity, String overwrittenCountry, List<ConflictField> differingFields) {
			this.hasConflict = hasConflict;
			this.overwrittenStreet = normalize(overwrittenStreet);
			this.overwrittenPostcode = normalize(overwrittenPostcode);
			this.overwrittenCity = normalize(overwrittenCity);
			this.overwrittenCountry = normalize(overwrittenCountry);
			this.differingFields = differingFields == null ? List.of() : Collections.unmodifiableList(differingFields);
		}

		ConflictAnalysis(boolean hasConflict, String overwrittenStreet, String overwrittenPostcode,
				String overwrittenCity, List<ConflictField> differingFields) {
			this(hasConflict, overwrittenStreet, overwrittenPostcode, overwrittenCity, "", differingFields);
		}

		boolean hasConflict() {
			return hasConflict;
		}

		String getOverwrittenStreet() {
			return overwrittenStreet;
		}

		String getOverwrittenPostcode() {
			return overwrittenPostcode;
		}

		String getOverwrittenCity() {
			return overwrittenCity;
		}

		String getOverwrittenCountry() {
			return overwrittenCountry;
		}

		List<ConflictField> getDifferingFields() {
			return differingFields;
		}
	}

	ConflictAnalysis analyze(
			OsmPrimitive building,
			String proposedStreet,
			String proposedPostcode,
			String proposedCity,
			String proposedCountry,
			String proposedHouseNumber,
			String proposedBuildingType
	) {
		String normalizedProposedStreet = normalize(proposedStreet);
		String normalizedProposedPostcode = normalize(proposedPostcode);
		String normalizedProposedCity = normalize(proposedCity);
		String normalizedProposedCountry = normalize(proposedCountry);
		if (building == null) {
			return new ConflictAnalysis(false,
					normalizedProposedStreet,
					normalizedProposedPostcode,
					normalizedProposedCity,
					normalizedProposedCountry,
					List.of());
		}

		String existingStreet = normalize(building.get("addr:street"));
		String existingPostcode = normalize(building.get("addr:postcode"));
		String existingCity = normalize(building.get("addr:city"));
		String existingCountry = normalize(building.get("addr:country"));
		String existingHouseNumber = normalize(building.get("addr:housenumber"));
		String existingBuildingType = normalize(building.get("building"));

		String normalizedProposedHouseNumber = normalize(proposedHouseNumber);
		String normalizedProposedBuildingType = normalize(proposedBuildingType);

		boolean streetConflict = !existingStreet.isEmpty() && !existingStreet.equals(normalizedProposedStreet);

		// Postcode conflict only matters when a new postcode is explicitly provided.
		boolean postcodeConflict = !normalizedProposedPostcode.isEmpty()
				&& !existingPostcode.isEmpty()
				&& !existingPostcode.equals(normalizedProposedPostcode);

		// City conflict only matters when a new city is explicitly provided.
		boolean cityConflict = !normalizedProposedCity.isEmpty()
				&& !existingCity.isEmpty()
				&& !existingCity.equals(normalizedProposedCity);

		// Country conflict only matters when a new country is explicitly provided.
		boolean countryConflict = !normalizedProposedCountry.isEmpty()
				&& !existingCountry.isEmpty()
				&& !existingCountry.equals(normalizedProposedCountry);

		boolean houseNumberDiff = !normalizedProposedHouseNumber.isEmpty()
				&& !existingHouseNumber.isEmpty()
				&& !existingHouseNumber.equals(normalizedProposedHouseNumber);

		boolean buildingTypeConflict = !normalizedProposedBuildingType.isEmpty()
				&& !existingBuildingType.isEmpty()
				&& !existingBuildingType.equals(normalizedProposedBuildingType)
				&& !"yes".equalsIgnoreCase(existingBuildingType);

		List<ConflictField> differingFields = new ArrayList<>();
		if (streetConflict) {
			differingFields.add(new ConflictField("addr:street", existingStreet, normalizedProposedStreet));
		}
		if (postcodeConflict) {
			differingFields.add(new ConflictField("addr:postcode", existingPostcode, normalizedProposedPostcode));
		}
		if (cityConflict) {
			differingFields.add(new ConflictField("addr:city", existingCity, normalizedProposedCity));
		}
		if (countryConflict) {
			differingFields.add(new ConflictField("addr:country", existingCountry, normalizedProposedCountry));
		}
		if (houseNumberDiff) {
			differingFields.add(new ConflictField("addr:housenumber", existingHouseNumber, normalizedProposedHouseNumber));
		}
		if (buildingTypeConflict) {
			differingFields.add(new ConflictField("building", existingBuildingType, normalizedProposedBuildingType));
		}

		String overwrittenStreet = existingStreet.isEmpty() ? normalizedProposedStreet : existingStreet;
		String overwrittenPostcode = existingPostcode.isEmpty() ? normalizedProposedPostcode : existingPostcode;
		String overwrittenCity = existingCity.isEmpty() ? normalizedProposedCity : existingCity;
		String overwrittenCountry = existingCountry.isEmpty() ? normalizedProposedCountry : existingCountry;
		return new ConflictAnalysis(
				streetConflict || postcodeConflict || cityConflict || countryConflict || buildingTypeConflict,
				overwrittenStreet,
				overwrittenPostcode,
				overwrittenCity,
				overwrittenCountry,
				differingFields
		);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
