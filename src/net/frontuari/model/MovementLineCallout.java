/**
 * 
 */
package net.frontuari.model;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.I_M_InOutLine;
import org.compiere.model.I_M_MovementLine;
import org.compiere.model.MInOutLine;

/**
 * @author dixon
 *
 */
public class MovementLineCallout implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab,
			GridField mField, Object value, Object oldValue) {
		int outLine_ID = (Integer) mTab.getValue(I_M_InOutLine.COLUMNNAME_M_InOutLine_ID);
		if(outLine_ID > 0) {
			MInOutLine line = new MInOutLine(ctx, outLine_ID, null);
			mTab.setValue(I_M_MovementLine.COLUMNNAME_M_Product_ID, line.getM_Product_ID());
			mTab.setValue(I_M_MovementLine.COLUMNNAME_M_Locator_ID, line.getM_Locator_ID());
			mTab.setValue(I_M_MovementLine.COLUMNNAME_MovementQty, line.getMovementQty());
			mTab.setValue(I_M_MovementLine.COLUMNNAME_M_AttributeSetInstance_ID, line.getM_AttributeSetInstance_ID());
		}
			
		return null;
	}
}
