package net.frontuari.component;

import net.frontuari.base.FTUFormFactory;
import net.frontuari.webui.apps.form.WFTUConfirmReceiptUI;

public class FormFactory extends FTUFormFactory {

	@Override
	protected void initialize() {
		//registerForm(WFTUConfirmnReceiptUI.class.getCanonicalName());
		registerForm(WFTUConfirmReceiptUI.class.getCanonicalName());
	}
}
