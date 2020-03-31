/******************************************************************************
 * Copyright (C) 2013 Elaine Tan                                              *
 * Copyright (C) 2013 Trek Global
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package net.frontuari.base;

import net.frontuari.webui.apps.form.WFTUCreateFromShipmentUI;
import net.frontuari.webui.apps.form.WFTUCreateInventoryMovementFromReceipt;

import org.compiere.grid.ICreateFrom;
import org.compiere.grid.ICreateFromFactory;
import org.compiere.model.GridTab;
import org.compiere.model.I_M_InOut;
import org.compiere.model.I_M_Movement;
import org.compiere.model.MWindow;
import org.compiere.util.Env;

/**
 * 
 * @author dixon
 *
 */
public class FTUCreateFromFactory implements ICreateFromFactory 
{

	@Override
	public ICreateFrom create(GridTab mTab) 
	{
		String tableName = mTab.getTableName();
		MWindow window = MWindow.get(Env.getCtx(), mTab.getGridWindow().getAD_Window_ID());
		if (window.getEntityType().equals("FTU01")) {
			if(tableName.equals(I_M_InOut.Table_Name))
				return new WFTUCreateFromShipmentUI(mTab);
			else if (I_M_Movement.Table_Name.equals(tableName))
				return new WFTUCreateInventoryMovementFromReceipt(mTab);
		}
		return null;
	}

}