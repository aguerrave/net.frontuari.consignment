package net.frontuari.grid;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Vector;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.model.MMovement;
import org.compiere.model.MMovementLine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;

public abstract class FTUCreateInventoryMovementFromReceipt extends CreateFrom {
	
	protected int M_Locator_ID;
	
	@Override
	public boolean dynInit() throws Exception {
		log.config("");
		setTitle(Msg.getElement(Env.getCtx(), "M_Movement_ID", false) + " .. " + Msg.translate(Env.getCtx(), "CreateFrom"));
		
		return true;
	}

	public void setM_Locator_ID(int M_Locator_ID) {
		this.M_Locator_ID = M_Locator_ID;
	}
	
	public FTUCreateInventoryMovementFromReceipt(GridTab gridTab) {
		super(gridTab);
		M_Locator_ID = 0;
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean save(IMiniTable miniTable, String trxName) {
		
		int tableRows = miniTable.getRowCount();
		
		for (int i = 0; i < tableRows; i++)
			if (((Boolean) miniTable.getValueAt(i, 0)).booleanValue()) //If Record Is Selected Then Save the Inventory Movement
				actionSave(miniTable, i, trxName);
		return true;
	}
	
	protected void actionSave(IMiniTable miniTable, int currRow, String trxName) {
		
		if(M_Locator_ID <= 0)
			throw new AdempiereException("@M_Locator_ID@ @IsMandatory@");
		
		//We get the fields from the table
		BigDecimal movementQty = (BigDecimal) miniTable.getValueAt(currRow, 1);
		String description = ((KeyNamePair) miniTable.getValueAt(currRow, 5)).getName();
		int M_Locator_From = ((KeyNamePair) miniTable.getValueAt(currRow, 8)).getKey();
		int M_Product_ID = ((KeyNamePair) miniTable.getValueAt(currRow, 6)).getKey();
		int M_InOut_ID = ((KeyNamePair) miniTable.getValueAt(currRow, 4)).getKey();
		int M_InOutLine_ID = ((KeyNamePair) miniTable.getValueAt(currRow, 5)).getKey();
		//---------------------------------------------------------------
		int M_Movement_ID = ((Integer) getGridTab().getValue("M_Movement_ID")).intValue();
		MMovement movement = new MMovement(Env.getCtx(), M_Movement_ID, trxName);
		movement.set_ValueOfColumn("M_InOut_ID", M_InOut_ID);
		movement.saveEx();
		//We create the line of movement
		
		MMovementLine line = new MMovementLine(movement);
		line.setMovementQty(movementQty);
		line.setDescription(description);
		line.setM_Locator_ID(M_Locator_From);
		line.setM_LocatorTo_ID(M_Locator_ID);
		line.setM_Product_ID(M_Product_ID);
		line.set_ValueOfColumn("M_InOutLine_ID", M_InOutLine_ID);
		
		line.saveEx();
	}
	
	protected void configureMiniTable(IMiniTable miniTable) {
		
		miniTable.setColumnClass(0, Boolean.class, false); //Selection
		miniTable.setColumnClass(1, BigDecimal.class, false); //Movement Qty Editable
		miniTable.setColumnClass(2, BigDecimal.class, true); //Available Qty
		miniTable.setColumnClass(3, BigDecimal.class, true); //Movement Qty
		miniTable.setColumnClass(4, KeyNamePair.class, true); //MInOut
		miniTable.setColumnClass(5, KeyNamePair.class, true); //IOLine
		miniTable.setColumnClass(6, KeyNamePair.class, true); //M_Product Data
		miniTable.setColumnClass(7, KeyNamePair.class, true); //UOM
		miniTable.setColumnClass(8, KeyNamePair.class, true); //M_Locator_ID From
		
		miniTable.autoSize();
	}
	
	public Vector<Vector<Object>> getMInOutData(int M_Inout_ID) {
		
		StringBuffer sql = new StringBuffer("SELECT")
								.append(" frm.AvailableQty")
								.append(", frm.MovementQty")
								.append(", frm.M_InOut_ID")
								.append(", frm.DocumentNo")
								.append(", frm.C_UOM_ID")
								.append(", frm.X12DE355")
								.append(", frm.M_InOutLine_ID")
								.append(", frm.Description")
								.append(", frm.M_Locator_ID")
								.append(", frm.WarehouseName")
								.append(", frm.X")
								.append(", frm.Y")
								.append(", frm.Z")
								.append(", frm.M_Product_ID")
								.append(", frm.ProductName")
							.append(" FROM FTU_RV_RelatedMovement frm")
							.append(" WHERE frm.M_InOut_ID = ? AND frm.AvailableQty > 0");
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		Vector<Vector<Object>> data = new Vector<Vector<Object>>(12);
		
		try {
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, M_Inout_ID);
			
			rs = pstmt.executeQuery();
			
			while (rs.next())
			{
				Vector<Object> line = new Vector<>(8);
				line.add(false); //IsSelected - 0
				line.add(rs.getBigDecimal("AvailableQty")); //Qty Movement - Editable - 1
				line.add(rs.getBigDecimal("AvailableQty")); //Qty Available - 2
				line.add(rs.getBigDecimal("MovementQty")); //Qty Movement - 3
				line.add(new KeyNamePair(rs.getInt("M_InOut_ID"), rs.getString("DocumentNo"))); //Receipt Data - 4
				line.add(new KeyNamePair(rs.getInt("M_InOutLine_ID"), rs.getString("Description"))); //IOLine - 5
				line.add(new KeyNamePair(rs.getInt("M_Product_ID"), rs.getString("ProductName")));//Product Data - 6
				line.add(new KeyNamePair(rs.getInt("C_UOM_ID"), rs.getString("X12DE355"))); //UOM Data - 7
				line.add(new KeyNamePair(rs.getInt("M_Locator_ID"),
						rs.getString("WarehouseName") + " X:" + rs.getString("X") + " - Y:" + rs.getString("Y") + " - Z:" + rs.getString("Z")));
				//Locator From Data - 8
				data.add(line);
			}
		} catch (SQLException e) {
			
			e.printStackTrace();
		} finally {
			DB.close(rs, pstmt);
		}
		
		return data;
	}
	
	protected Vector<String> getOISColumnNames()
	{
		//  Header Info
	    Vector<String> columnNames = new Vector<String>(7);
	    columnNames.add(Msg.translate(Env.getCtx(), "IsSelected"));
	    columnNames.add(Msg.translate(Env.getCtx(), "Quantity"));
	    columnNames.add(Msg.translate(Env.getCtx(), "AvailableQty"));
	    columnNames.add(Msg.translate(Env.getCtx(), "MovementQty"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "M_InOut_ID", false));
	    columnNames.add(Msg.translate(Env.getCtx(), "Description"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "M_Product_ID"));
	    columnNames.add(Msg.translate(Env.getCtx(), "C_UOM_ID"));
	    columnNames.add(Msg.getElement(Env.getCtx(), "M_Locator_ID"));
	    
	    return columnNames;
	}
	
	public ArrayList<KeyNamePair> loadMLocatorToData() {
		
		StringBuffer sql = new StringBuffer("SELECT loc.M_Locator_ID")
							.append(", wh.Value || ' - ' || wh.Name || ' - ' || loc.Value || ' - X:' || loc.X || ' - Y:' || loc.Y || ' - Z:' || loc.Z")
							.append(" FROM M_Locator loc")
							.append(" INNER JOIN M_Warehouse wh ON wh.M_Warehouse_ID = loc.M_Warehouse_ID")
							.append(" WHERE wh.AD_Org_ID = ? AND wh.IsConsignmentWarehouse = 'N'")
							.append(" ORDER BY wh.M_Warehouse_ID");
		
		ArrayList<KeyNamePair> data = new ArrayList<>(12);
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		data.add(new KeyNamePair(0, ""));
		
		try {
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, Env.getContextAsInt(Env.getCtx(), getGridTab().getWindowNo(), "AD_Org_ID"));
			
			rs = pstmt.executeQuery();
			
			while (rs.next())
			{
				data.add(new KeyNamePair(rs.getInt(1), rs.getString(2)));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			DB.close(rs, pstmt);
		}
		
		return data;
	}
	
	public ArrayList<KeyNamePair> loadMInOutData() {
		
		StringBuffer sql = new StringBuffer("SELECT io.M_Inout_ID, io.DocumentNo||' - '||TO_CHAR(io.MovementDate,'DD/MM/YYYY') AS DocumentNo")
							.append(" FROM M_InOut io")
							.append(" WHERE io.DocStatus = 'CO' AND EXISTS (SELECT 1 FROM FTU_RV_RelatedMovement rmv")
							.append("				WHERE rmv.M_InOut_ID = io.M_InOut_ID AND rmv.AvailableQty > 0)");
		
		ArrayList<KeyNamePair> inOutData = new ArrayList<>(12);
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		inOutData.add(new KeyNamePair(0, ""));
		
		try {
			pstmt = DB.prepareStatement(sql.toString(), null);
			
			rs = pstmt.executeQuery();
			
			while (rs.next())
			{
				inOutData.add(new KeyNamePair(rs.getInt(1), rs.getString(2)));
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			DB.close(rs, pstmt);
		}
		
		return inOutData;
	}
}
