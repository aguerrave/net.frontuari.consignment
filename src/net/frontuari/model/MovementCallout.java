/**
 * 
 */
package net.frontuari.model;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.I_M_InOut;
import org.compiere.model.I_M_InOutLine;
import org.compiere.model.I_M_Movement;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;

/**
 * @author dixon
 *
 */
public class MovementCallout implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab,
			GridField mField, Object value, Object oldValue) {
		if(mField.getColumnName().equals(I_M_InOut.COLUMNNAME_M_InOut_ID)) {
			int inOut_ID = (Integer) value;
			int movementID = (mTab.getValue(I_M_Movement.COLUMNNAME_M_Movement_ID) != null ? 
					(Integer) mTab.getValue(I_M_Movement.COLUMNNAME_M_Movement_ID) 
					: 0) ;
			
			if(inOut_ID > 0
					&& movementID > 0) {
				MInOut inOut = new MInOut(ctx, inOut_ID, null);
				MMovement parent = new MMovement(ctx, movementID, null);
				for (MInOutLine line : inOut.getLines()) {
					MMovementLine movementLine = new MMovementLine(parent);
					movementLine.set_ValueOfColumn(I_M_InOutLine.COLUMNNAME_M_InOutLine_ID, line.getM_InOutLine_ID());
					movementLine.setM_Product_ID(line.getM_Product_ID());
					movementLine.setMovementQty(line.getQtyEntered());
					movementLine.saveEx();
				}
			}
		}
		return null;
	}
}
