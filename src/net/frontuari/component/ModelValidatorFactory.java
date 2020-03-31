package net.frontuari.component;

import net.frontuari.base.FTUModelValidatorFactory;
import net.frontuari.model.FTUModelValidator;

public class ModelValidatorFactory extends FTUModelValidatorFactory {

	@Override
	protected void initialize() {
		registerModelValidator(FTUModelValidator.class);
	}

}
