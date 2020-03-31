package net.frontuari.grid;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.apps.IStatusBar;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MProduct;
import org.compiere.model.MUOM;
import org.compiere.model.MUOMConversion;
import org.compiere.process.DocAction;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;

import net.frontuari.base.FTUForm;

public abstract class FTUConfirmReceiptController extends FTUForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	protected int m_InOut_ID = 0;
	protected int m_Product_ID = 0;
	
	protected int selected = 0;
	
	//------Number of Lines Confirmed and Unconfirmed-----------
	protected int linesConfirmed = 0;
	protected int linesUnconfirmed = 0;
	//----------------------------------------------------------
	
	public void setNumberLines() {
		
		StringBuffer sql = new StringBuffer("SELECT")
							.append(" SUM")
								.append("(")
									.append("CASE WHEN iol.IsConfirmed = 'Y' THEN 1")
									.append(" ELSE 0 END")
								.append(") AS LinesConfirmed")
							.append(", SUM")
								.append("(")
									.append("CASE WHEN iol.IsConfirmed = 'N' THEN 1")
									.append(" ELSE 0 END")
								.append(") AS LinesUnConfirmed")
							.append(" FROM M_InOutLine iol")
							.append(" WHERE iol.M_InOut_ID = ?");
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try {
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, m_InOut_ID);
			
			rs = pstmt.executeQuery();
			
			if (rs.next())
			{
				linesConfirmed = rs.getInt("LinesConfirmed");
				linesUnconfirmed = rs.getInt("LinesUnConfirmed");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			DB.close(rs, pstmt);
		}
	}
	
	public Vector<Vector<Object>> loadMInOutLineData() {
		
		StringBuffer sql = new StringBuffer("SELECT")
							.append(" iol.M_InoutLine_ID")
							.append(", iol.C_UOM_ID")
							.append(", uom.x12de355")
							.append(", io.DocumentNo")
							.append(" FROM M_InOutLine iol")
							.append(" INNER JOIN M_InOut io ON io.M_InOut_ID = iol.M_InOut_ID")
							.append(" INNER JOIN C_UOM uom ON uom.C_UOM_ID = iol.C_UOM_ID")
							.append(" WHERE iol.M_InOut_ID = ? AND iol.M_Product_ID = ?")
							.append(" AND iol.IsConfirmed = 'N'")
							.append(" ORDER BY iol.Line");
		
		Vector<Vector<Object>> data = new Vector<>(12);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try {
			pstmt = DB.prepareStatement(sql.toString(), null);
			pstmt.setInt(1, m_InOut_ID);
			pstmt.setInt(2, m_Product_ID);
			
			rs = pstmt.executeQuery();
			
			while (rs.next())
			{
				Vector<Object> row = new Vector<>(4);
				
				row.add(true); //Selected
				row.add(Env.ZERO); //QtyEntered
				row.add(new KeyNamePair(rs.getInt("C_UOM_ID"), rs.getString("x12de355"))); //C_UOM
				row.add(new KeyNamePair(rs.getInt("M_InOutLine_ID"), rs.getString("DocumentNo"))); //DocNo And Line
				
				data.add(row);
			}
		} catch (SQLException e) {
			System.out.println(e);
		} finally {
			DB.close(rs, pstmt);
		}
		
		return data;
	}
	
	public void setStatusBar(IStatusBar statusBar) {
		
		statusBar.setStatusLine(Msg.getMsg(Env.getCtx(), "Number.Lines", new Object[] { linesConfirmed, linesUnconfirmed } ));
	}
	
	public Vector<String> getOISColumnNames() {
		
		Vector<String> columnNames = new Vector<>(4);
		
		columnNames.add(Msg.getElement(Env.getCtx(), "IsSelected")); // 0
		columnNames.add(Msg.getElement(Env.getCtx(), "QtyEntered")); // 1
		columnNames.add(Msg.getElement(Env.getCtx(), "C_UOM_ID")); // 2
		columnNames.add(Msg.getElement(Env.getCtx(), "DocumentNo")); // 3
		
		return columnNames;
	}
	
	public void completeInOut(String trxName) {
		
		MInOut currInOut = new MInOut(Env.getCtx(), m_InOut_ID, trxName);
		
		// Check if fail on other trx and prepare document
		if(currInOut.getDocStatus().equals(MInOut.STATUS_Invalid))
		{
			currInOut.setDocStatus(MInOut.STATUS_InProgress);
			currInOut.setDocAction(MInOut.DOCACTION_Complete);
			currInOut.saveEx();
		}
		
		if (!currInOut.processIt(DocAction.ACTION_Complete) || !DocAction.STATUS_Completed.equals(currInOut.getDocStatus()))
			throw new AdempiereException(currInOut.getProcessMsg());
		
		currInOut.saveEx();
	}
	
	public void confirmLine(IMiniTable miniTable, String trxName) {
		
		int size = miniTable.getRowCount();
		
		for (int i = 0; i < size; i++)
			if (((Boolean) miniTable.getValueAt(i, 0)).booleanValue())
				actionSave(miniTable, i, trxName);
	}
	
	public void configureMiniTable(IMiniTable miniTable) {
		
		miniTable.setColumnClass(0, Boolean.class, true);
		miniTable.setColumnClass(1, BigDecimal.class, false); //QtyEntered
		miniTable.setColumnClass(2, KeyNamePair.class, true); // C_UOM
		miniTable.setColumnClass(3, KeyNamePair.class, true); //DocNo and Line_ID
		
		//miniTable.autoSize();
	}
	
	protected void actionSave(IMiniTable miniTable, int currRow, String trxName) {
		
		int M_InOutLine_ID = ((KeyNamePair) miniTable.getValueAt(currRow, 3)).getKey();
		MInOutLine currLine = new MInOutLine(Env.getCtx(), M_InOutLine_ID, trxName);
		BigDecimal qtyEntered = (BigDecimal) miniTable.getValueAt(currRow, 1);
		BigDecimal confirmedqty = (BigDecimal) currLine.get_Value("QtyReceipt");
		
		// Validate QtyEntered < ConfirmedQty
		if(qtyEntered.compareTo(confirmedqty)<=0)
		{
			MProduct product = ((MProduct) currLine.getM_Product());
			int C_UOM_Product_ID = product.getC_UOM_ID();
			int C_UOM_ID = ((KeyNamePair) miniTable.getValueAt(currRow, 2)).getKey();
			int precision = MUOM.getPrecision(Env.getCtx(), C_UOM_ID);
			
			if (qtyEntered.precision() != precision)
				qtyEntered = qtyEntered.setScale(precision, BigDecimal.ROUND_HALF_UP);
			
			currLine.set_ValueOfColumn("IsConfirmed", "Y");
			currLine.setQtyEntered(qtyEntered);
			
			if (C_UOM_Product_ID == C_UOM_ID)
				currLine.setMovementQty(qtyEntered);
			else
				currLine.setMovementQty(MUOMConversion.convertProductFrom(Env.getCtx(), product.get_ID(), C_UOM_ID, qtyEntered));
			
			currLine.saveEx();
		}
		else
		{
			throw new AdempiereException(Msg.translate(Env.getCtx(),"Error")+": "+Msg.translate(Env.getCtx(),"QtyEntered")+" > "+Msg.translate(Env.getCtx(),"ConfirmedQty"));
		}
	}
}
