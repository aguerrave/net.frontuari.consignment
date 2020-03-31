package net.frontuari.model;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.I_C_Order;
import org.compiere.model.MTab;
import org.compiere.util.DB;

public class OrderCallout implements IColumnCallout  {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab,
			GridField mField, Object value, Object oldValue) {
		if(mField.getColumnName().equals(I_C_Order.COLUMNNAME_AD_Org_ID)) {
			int OrgID = (Integer) value;
			
			MTab tab = new MTab(ctx, mTab.getAD_Tab_ID(), null);
			if(tab.getEntityType().equals("FTU01"))
			{
				//Get the consignment Warehouse
				Integer warehouseID = DB.getSQLValue(null, "SELECT MAX(M_Warehouse_ID) FROM M_Warehouse WHERE AD_Org_ID = ?", OrgID);
				mTab.setValue("M_Warehouse_ID", warehouseID);
			}
			
			
		}
		return null;
	}

}
