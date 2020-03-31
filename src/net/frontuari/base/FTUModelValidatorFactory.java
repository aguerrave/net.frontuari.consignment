package net.frontuari.base;

import java.lang.reflect.Constructor;
import java.util.HashMap;

import org.adempiere.base.IModelValidatorFactory;
import org.compiere.model.ModelValidator;

public abstract class FTUModelValidatorFactory implements
		IModelValidatorFactory {
	
	private HashMap<String, Class<? extends ModelValidator>> cacheModelValidator = new HashMap<>();
	
	
	protected void registerModelValidator(Class<? extends ModelValidator> cl) {
		
		cacheModelValidator.put(cl.getCanonicalName(), cl);
	}
	
	public FTUModelValidatorFactory() {
		
		initialize();
	}
	
	protected abstract void initialize();
	
	@Override
	public ModelValidator newModelValidatorInstance(String className) {
		
		Class<? extends ModelValidator> cl = cacheModelValidator.get(className);
		ModelValidator retVal = null;
		
		if (cl == null)
			return null;
		
		try {
			Constructor<? extends ModelValidator> construct = cl.getConstructor();
			retVal = construct.newInstance();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return retVal;
	}

}
