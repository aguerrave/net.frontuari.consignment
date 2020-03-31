/**
 * 
 */
package net.frontuari.component;

import java.util.ArrayList;
import java.util.List;

import net.frontuari.model.MovementCallout;
import net.frontuari.model.MovementLineCallout;
import net.frontuari.model.OrderCallout;

import org.adempiere.base.IColumnCallout;
import org.adempiere.base.IColumnCalloutFactory;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_M_InOutLine;
import org.compiere.model.I_M_Movement;
import org.compiere.model.I_M_MovementLine;

/**
 * @author dixon
 *
 */
public class CalloutFactory implements IColumnCalloutFactory {

	@Override
	public IColumnCallout[] getColumnCallouts(String tableName,
			String columnName) {
		List<IColumnCallout> list = new ArrayList<IColumnCallout>();

		switch (tableName) {
		case I_M_Movement.Table_Name:
			switch (columnName) {
			case I_M_InOutLine.COLUMNNAME_M_InOut_ID:
				list.add(new MovementCallout());
				break;
			}
			break;
		case I_M_MovementLine.Table_Name:
			switch (columnName) {
			case I_M_InOutLine.COLUMNNAME_M_InOutLine_ID:
				list.add(new MovementLineCallout());
				break;
			}
			break;
		case I_C_Order.Table_Name:
			switch (columnName) {
			case I_C_Order.COLUMNNAME_AD_Org_ID:
				list.add(new OrderCallout());
				break;
			}
			break;
		}

		return list.isEmpty() ? new IColumnCallout[0] : list
				.toArray(new IColumnCallout[0]);
	}

}
