package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.common.error.ValidationException;
import org.junit.jupiter.api.Test;

class ResourceLocationValidationTest {

	@Test
	void acceptsValidHalifaxCoordinates() {
		ResourceLocationValidation.Normalized result =
				ResourceLocationValidation.validate(new ResourceLocationRequest(44.6488, -63.5752));

		assertThat(result.latitude()).isEqualTo(44.6488);
		assertThat(result.longitude()).isEqualTo(-63.5752);
	}

	@Test
	void acceptsBoundaryLatitudeValues() {
		assertThat(ResourceLocationValidation.validate(new ResourceLocationRequest(-90.0, 0.0)).latitude())
				.isEqualTo(-90.0);
		assertThat(ResourceLocationValidation.validate(new ResourceLocationRequest(90.0, 0.0)).latitude())
				.isEqualTo(90.0);
	}

	@Test
	void acceptsBoundaryLongitudeValues() {
		assertThat(ResourceLocationValidation.validate(new ResourceLocationRequest(0.0, -180.0)).longitude())
				.isEqualTo(-180.0);
		assertThat(ResourceLocationValidation.validate(new ResourceLocationRequest(0.0, 180.0)).longitude())
				.isEqualTo(180.0);
	}

	@Test
	void acceptsZeroCoordinates() {
		ResourceLocationValidation.Normalized result =
				ResourceLocationValidation.validate(new ResourceLocationRequest(0.0, 0.0));

		assertThat(result.latitude()).isZero();
		assertThat(result.longitude()).isZero();
	}

	@Test
	void acceptsNegativeLongitude() {
		assertThat(ResourceLocationValidation.validate(new ResourceLocationRequest(10.0, -120.0)).longitude())
				.isEqualTo(-120.0);
	}

	@Test
	void rejectsMissingLatitude() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(null, -63.5752)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsMissingLongitude() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(44.6488, null)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsANullBody() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(null)).isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsLatitudeBelowMinimum() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(-90.0001, 0.0)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsLatitudeAboveMaximum() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(90.0001, 0.0)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsLongitudeBelowMinimum() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(0.0, -180.0001)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsLongitudeAboveMaximum() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(0.0, 180.0001)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsNaNLatitude() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(new ResourceLocationRequest(Double.NaN, 0.0)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsPositiveInfinityLatitude() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(
				new ResourceLocationRequest(Double.POSITIVE_INFINITY, 0.0)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsNegativeInfinityLongitude() {
		assertThatThrownBy(() -> ResourceLocationValidation.validate(
				new ResourceLocationRequest(0.0, Double.NEGATIVE_INFINITY)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void reportsBothFieldsWhenBothAreInvalid() {
		try {
			ResourceLocationValidation.validate(new ResourceLocationRequest(-91.0, 181.0));
		} catch (ValidationException ex) {
			assertThat(ex.getFieldErrors()).containsKeys("latitude", "longitude");
			return;
		}
		throw new AssertionError("Expected a ValidationException");
	}

}
