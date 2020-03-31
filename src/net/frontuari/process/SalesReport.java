package net.frontuari.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.logging.Level;

import net.frontuari.base.FTUProcess;
import net.frontuari.model.FTUMatchPO;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MDocType;
import org.compiere.model.MInventoryLine;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * Sales Report
 *
 * @author Fernanda Carrillo
 * 
 * 
 * 
 */
public class SalesReport extends FTUProcess {
	/** Warehouse */
	private int p_AD_Org_ID = 0;
	/** Warehouse */
	private int p_M_Warehouse_ID = 0;
	/** BPartner Vendor */
	private int p_C_BPartner_ID = 0;
	/** Date Invoice */
	private Timestamp p_DateInvoiced1 = null;
	private Timestamp p_DateInvoiced2 = null;
	/** Create Document from sales */
	private boolean p_CreateDocument = false;
	/** Document Type */
	private int p_C_DocType_ID = 0;
	/** Document Status */
	private String p_IsGenerated = null; 
	/** Type to related invoice or internal use inventory */
	private String p_Type = null;
	/** Return Info */
	private StringBuffer	m_info = new StringBuffer();
	/** info string*/
	private StringBuilder info = new StringBuilder();

	/**
	 * Prepare - e.g., get Parameters.
	 */
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("AD_Org_ID"))
				p_AD_Org_ID = para[i].getParameterAsInt();
			else if (name.equals("M_Warehouse_ID"))
				p_M_Warehouse_ID = para[i].getParameterAsInt();
			else if (name.equals("C_BPartner_ID"))
				p_C_BPartner_ID = para[i].getParameterAsInt();
			else if (name.equals("DateInvoiced")) {
				p_DateInvoiced1 = para[i].getParameterAsTimestamp();
				p_DateInvoiced2 = para[i].getParameter_ToAsTimestamp();
			} else if (name.equals("CreateConfirm"))
				p_CreateDocument = para[i].getParameterAsBoolean();
			else if (name.equals("C_DocType_ID"))
				p_C_DocType_ID = para[i].getParameterAsInt();
			else if (name.equals("IsGenerated"))
				p_IsGenerated = para[i].getParameterAsString();
			else if (name.equals("Type"))
				p_Type = para[i].getParameterAsString();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	} // prepare

	/**
	 * Perform process.
	 * 
	 * @return Message
	 * @throws Exception
	 *             if not successful
	 */
	protected String doIt() throws Exception {
		if (p_CreateDocument && p_C_DocType_ID <= 0)
			throw new AdempiereUserError("@FillMandatory@ @C_DocType_ID@");
		//
		insertRecords();
		if (p_CreateDocument) {
			createO();
		}

		return m_info.toString();
	} // doIt

	/**
	 * 	
	 */
	private void insertRecords() {
		
		int no = 0;
		// get invoiced by date range
			StringBuilder sqlMovements = new StringBuilder(
								",COALESCE((SELECT SUM(Qty) FROM adempiere.FTU_RV_ConsignmentMovement cm"
										+ " WHERE cm.M_Product_ID = i.M_Product_ID "
										+ "AND cm.AD_Org_ID = i.AD_Org_ID ")
								.append(" AND cm.MovementDate <= ")
								.append(DB.TO_DATE(p_DateInvoiced2, true));
						if(p_C_BPartner_ID > 0)
							sqlMovements.append(" AND C_BPartner_ID = "+p_C_BPartner_ID);
						sqlMovements.append("),0) AS MovementQty ");

		StringBuilder sqlInvoice = new StringBuilder("SELECT * "+sqlMovements.toString()
				+ "FROM adempiere.FTU_RV_Invoices i WHERE i.DateInvoiced BETWEEN  ")
				.append(DB.TO_DATE(p_DateInvoiced1, true)).append(" AND ")
				.append(DB.TO_DATE(p_DateInvoiced2, true));
		if(p_AD_Org_ID > 0)
			sqlInvoice.append(" AND i.AD_Org_ID = ").append(p_AD_Org_ID);
		if(p_IsGenerated != null)
			sqlInvoice.append(" AND i.IsGenerated = '").append(p_IsGenerated).append("' ");
		sqlInvoice.append("  ORDER BY i.DateInvoiced ");
		PreparedStatement pstmt1 = null;
		ResultSet rs1 = null;
		try {
			pstmt1 = DB.prepareStatement(sqlInvoice.toString(), get_TrxName());
			rs1 = pstmt1.executeQuery();
			int M_Product_ID = 0;
			BigDecimal movementQty = BigDecimal.ZERO;
			BigDecimal qtyInvoiced = BigDecimal.ZERO;
			BigDecimal SOQty = BigDecimal.ZERO;
			while (rs1.next()) {
				
					qtyInvoiced = rs1.getBigDecimal("QtyInvoiced");
					SOQty = BigDecimal.ZERO;
					// get movements by product and date range
					if (rs1.getInt("M_Product_ID") != M_Product_ID) {
					/*	StringBuilder sqlMovements = new StringBuilder(
								"SELECT SUM(Qty) AS MovementQty FROM adempiere.FTU_RV_ConsignmentMovement"
										+ " WHERE M_Product_ID = ")
								.append(rs1.getInt("M_Product_ID"))
								.append(" AND AD_Org_ID = ")
								.append(rs1.getInt("AD_Org_ID"))
								.append(" AND MovementDate <= ")
								.append(DB.TO_DATE(p_DateInvoiced2, true));
						if(p_C_BPartner_ID > 0)
							sqlMovements.append(" AND C_BPartner_ID = "+p_C_BPartner_ID);
						movementQty = DB.getSQLValueBD(get_TrxName(),
								sqlMovements.toString());*/
						movementQty = rs1.getBigDecimal("MovementQty");
						M_Product_ID = rs1.getInt("M_Product_ID");
						if(movementQty == null)
							movementQty = BigDecimal.ZERO;
					}
					boolean isGenerated = (rs1.getString("IsGenerated").equals("Y")? true: false);
					if (movementQty.compareTo(BigDecimal.ZERO) > 0 ) {
						// discount qtyinvoiced
						if(isGenerated){
							SOQty = qtyInvoiced;
						}else if (movementQty.compareTo(qtyInvoiced) >= 0 && !isGenerated) {
							movementQty = movementQty.subtract(qtyInvoiced);
							SOQty = qtyInvoiced;
						} else if (movementQty.compareTo(qtyInvoiced) == -1 && !isGenerated){
							SOQty = movementQty;
							movementQty = BigDecimal.ZERO;
						}
					
						
						// insert records with SOQty
						StringBuilder insert = new StringBuilder(
								"INSERT INTO adempiere.T_Sales ");
						insert.append(
								" ( AD_Client_ID, AD_PInstance_ID, SOQty, AD_Org_ID, C_Invoice_ID, IsPaid, DateAcct,DocStatus, GrandTotal, C_DocTypeTarget_ID, ")
								.append("C_Order_ID, Description, DateInvoiced, DocumentNo ,M_PriceList_ID, C_BPartner_Location_ID, C_BPartner_ID, C_ConversionType_ID,C_Currency_ID, ")
								.append("C_DocType_ID, M_InOutLine_ID, PriceActual, C_UOM_ID, Line, QtyInvoiced, C_InvoiceLine_ID, TaxAmt, LineTotalAmt, C_OrderLine_ID, PriceEntered, QtyEntered, ")
								.append("PriceLimit, M_Product_ID, M_InOut_ID, M_Warehouse_ID, M_Locator_ID, IsConsignmentProduct, SalesRep_ID, PaymentRule,"
										+ " C_DocTypeInvoice_ID, M_WarehouseSource_ID, C_BPartner_BPartner_Parent_ID, IsGenerated, Type, PO_PriceList_ID, PriceStd, IsSOTrx, M_Inventory_ID, M_InventoryLine_ID ) ")
								.append(" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"); 
						
						Object[] params = (new Object[] {
							rs1.getInt("AD_Client_ID"), // #1
							getAD_PInstance_ID(), // # 2
							SOQty, // # 3
							(p_AD_Org_ID > 0 ? p_AD_Org_ID: rs1.getInt("AD_Org_ID")), // To Order and Invoice # 4
							(rs1.getInt("C_Invoice_ID")==0 ? null : rs1.getInt("C_Invoice_ID")), // # 5
							rs1.getString("IsPaid"), // # 6
							rs1.getTimestamp("DateAcct"), // # 7
							rs1.getString("DocStatus"), // # 8
							rs1.getInt("GrandTotal"), // # 9
							(rs1.getInt("C_DocTypeTarget_ID")==0 ? null : rs1.getInt("C_DocTypeTarget_ID")), // To Invoice # 10
							rs1.getInt("C_Order_ID"), // # 11
							rs1.getString("Description"), // # 12
							rs1.getTimestamp("DateInvoiced"), // # 13
							rs1.getString("DocumentNo"), // # 14
							(rs1.getInt("M_PriceList_ID")==0 ? null : rs1.getInt("M_PriceList_ID")), // # 15
							(rs1.getInt("C_BPartner_Location_ID")==0 ? null : rs1.getInt("C_BPartner_Location_ID")), // # 16
							(p_C_BPartner_ID==0 ? null : p_C_BPartner_ID), // To OC # 17
							(rs1.getInt("C_ConversionType_ID")==0 ? null : rs1.getInt("C_ConversionType_ID")), // # 18
							(rs1.getInt("C_Currency_ID")==0 ? null : rs1.getInt("C_Currency_ID")), // # 19
							(p_C_DocType_ID==0 ? null : p_C_DocType_ID), // To OC # 20
							(rs1.getInt("M_InOutLine_ID")==0 ? null : rs1.getInt("M_InOutLine_ID")), // # 21
							rs1.getBigDecimal("PriceActual"), // # 22
							(rs1.getInt("C_UOM_ID")==0 ? null : rs1.getInt("C_UOM_ID")), // # 23
							rs1.getInt("Line"), // # 24
							rs1.getBigDecimal("QtyInvoiced"), // # 25
							(rs1.getInt("C_InvoiceLine_ID")==0 ? null : rs1.getInt("C_InvoiceLine_ID")), // # 26
							rs1.getBigDecimal("TaxAmt"), // # 27
							rs1.getBigDecimal("LineTotalAmt"), // # 28
							(rs1.getInt("C_OrderLine_ID")==0 ? null : rs1.getInt("C_OrderLine_ID")), // # 29
							rs1.getBigDecimal("PriceEntered"), // # 30
							rs1.getBigDecimal("QtyEntered"), // # 31
							rs1.getBigDecimal("PriceLimit"), // # 32
							(rs1.getInt("M_Product_ID")==0 ? null : rs1.getInt("M_Product_ID"))	, // # 33
							(rs1.getInt("M_InOut_ID")==0 ? null : rs1.getInt("M_InOut_ID")), // # 34
							(p_M_Warehouse_ID==0 ? null : p_M_Warehouse_ID), // To OC # 35
							(rs1.getInt("M_Locator_ID")==0 ? null : rs1.getInt("M_Locator_ID")), // # 36
							rs1.getString("IsConsignmentProduct"), // # 37
							(rs1.getInt("SalesRep_ID")==0 ? null : rs1.getInt("SalesRep_ID")), // # 38
							rs1.getString("PaymentRule"), // # 39 
							(rs1.getInt("C_DocType_ID")==0 ? null : rs1.getInt("C_DocType_ID")), // # 40,
							(rs1.getInt("M_Warehouse_ID")==0 ? null : rs1.getInt("M_Warehouse_ID")), // # 41
							(rs1.getInt("C_BPartner_ID")==0 ? null : rs1.getInt("C_BPartner_ID")), // # 42
							rs1.getString("IsGenerated"), // # 43
							rs1.getString("Type"), // # 44
							(rs1.getInt("PO_PriceList_ID")==0 ? null : rs1.getInt("PO_PriceList_ID")), // # 45
							rs1.getBigDecimal("PriceStd"), // # 46
							rs1.getString("IsSoTrx"), // # 47
							(rs1.getInt("M_Inventory_ID")==0 ? null : rs1.getInt("M_Inventory_ID")), // # 48
							(rs1.getInt("M_InventoryLine_ID")==0 ? null : rs1.getInt("M_InventoryLine_ID")) // # 49
						});
						
						no = DB.executeUpdate(insert.toString(), params, false, get_TrxName());
						if (log.isLoggable(Level.FINE))
							log.fine("Insert (1) #" + no);
						
				}	
			}// end while

		} catch (Exception e) {
			log.log(Level.SEVERE, "", e);
		} finally {
			DB.close(rs1, pstmt1);
			rs1 = null;
			pstmt1 = null;
		}
	} // prepareTable

	/**
	 * Create Order
	 */
	private String createO() throws Exception {
		int noOrders = 0;
		StringBuilder sql = new StringBuilder( "SELECT * FROM adempiere.T_Sales "
				+ " WHERE IsGenerated = 'N' "
					+ " AND DateInvoiced BETWEEN ?  AND ? "
					+ " AND AD_PInstance_ID = ? ");
		if (p_Type != null)
			sql.append(" AND Type = ? ");
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		MOrder order = null;
		MOrderLine orderLine = null;
		MInvoiceLine il= null;
		MInventoryLine inl = null;
		int recordId = 0;
		String columnName = "";
		int line = 10;
		try {
			pstmt = DB.prepareStatement(sql.toString(), get_TrxName());			
			pstmt.setTimestamp(1, p_DateInvoiced1);
			pstmt.setTimestamp(2, p_DateInvoiced2);
			pstmt.setInt(3, getAD_PInstance_ID());
			if(p_Type != null)
				pstmt.setString(4, p_Type);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				MBPartner bp = new MBPartner(getCtx(), p_C_BPartner_ID, get_TrxName());
				if (order == null) {
					order = new MOrder(getCtx(), 0, get_TrxName());
					order.setAD_Org_ID(p_AD_Org_ID > 0 ? p_AD_Org_ID : rs
							.getInt("AD_Org_ID"));
					order.setC_DocTypeTarget_ID(p_C_DocType_ID);
					order.setDescription(Msg.getMsg(Env.getCtx(), "Related")+" : "+p_DateInvoiced1+" - "+p_DateInvoiced2);
					order.setDateOrdered(Env
							.getContextAsDate(getCtx(), "#Date"));
					order.setC_BPartner_ID(p_C_BPartner_ID);
					order.setC_BPartner_Location_ID(getLocation(
							p_C_BPartner_ID, "IsShipTo"));
					order.setBill_BPartner_ID(p_C_BPartner_ID);
					order.setBill_Location_ID(getLocation(p_C_BPartner_ID,
							"IsBillTo"));
					order.setM_Warehouse_ID(p_M_Warehouse_ID > 0 ? p_M_Warehouse_ID
							: rs.getInt("M_Warehouse_ID"));
					order.setC_Currency_ID(rs.getInt("C_Currency_ID"));
					order.setSalesRep_ID(rs.getInt("SalesRep_ID"));
					order.setPaymentRule(rs.getString("PaymentRule"));
					order.setM_PriceList_ID((bp.getPO_PriceList_ID()==0 ? rs.getInt("PO_PriceList_ID") : bp.getPO_PriceList_ID()));
					order.setIsSOTrx(false);
					order.setC_PaymentTerm_ID(1000016); // Buscar Terminos de Pago que no sean Manuales y que no tengan plan de amortizacion, personalizacion biomercados.
					order.saveEx(get_TrxName());
				}
				
				BigDecimal SOQty = rs.getBigDecimal("SOQty");
				// validate type of record
				String type = rs.getString("Type");
				
				if(type.equals("INV")){
					columnName = "C_InvoiceLine_ID";
					recordId = rs.getInt("C_InvoiceLine_ID");
					il = new MInvoiceLine(getCtx(), recordId, get_TrxName());
				}else if (type.equals("IUI")){
					columnName = "M_InventoryLine_ID";
					recordId = rs.getInt("M_InventoryLine_ID");
					inl = new MInventoryLine(getCtx(), recordId, get_TrxName());
				}
				// create lines
				orderLine = new MOrderLine(order);
				
				BigDecimal priceStd = DB.getSQLValueBD(get_TrxName(), 
						"SELECT PriceStd FROM M_ProductPrice pp JOIN M_PriceList_Version plv ON pp.M_PriceList_Version_ID = plv.M_PriceList_Version_ID WHERE plv.M_PriceList_ID = ? AND pp.M_Product_ID = ? AND plv.ValidFrom <= ? ORDER BY plv.ValidFrom DESC", 
						new Object[]{bp.getPO_PriceList_ID(),rs.getInt("M_Product_ID"), rs.getTimestamp("DateInvoiced")});
				
				orderLine.setLine(line);
				orderLine.setM_Product_ID(rs.getInt("M_Product_ID"), true);
				orderLine.setQtyEntered(SOQty);
				orderLine.setQtyOrdered(SOQty);
				orderLine.setPrice(priceStd);
				orderLine.set_ValueOfColumn(columnName, recordId);
				orderLine.saveEx(get_TrxName());
				// add reference to final order
				DB.executeUpdate(" UPDATE T_Sales "
								+ " SET C_OrderSourceValue =  '"+order.getDocumentNo()+"' WHERE "+columnName+" = "+recordId, get_TrxName());

				line += 10;
				String orgclause ="";
				if(p_AD_Org_ID>0)
				orgclause = " <= ? AND AD_Org_ID ";
				String sqlMPO = "SELECT * FROM FTU_RV_ConsignmentMovement WHERE M_Product_ID = ? AND MovementDate = ? "+orgclause+" AND Qty <> 0 ORDER BY Created ASC";
				PreparedStatement psMPO = null;
				ResultSet rsMPO = null;
				try{
					psMPO = DB.prepareStatement(sqlMPO, get_TrxName());
					if(il != null){
						psMPO.setInt(1, il.getM_Product_ID());
					}else{
						psMPO.setInt(1, inl.getM_Product_ID());
					}
					if(il != null){
						psMPO.setTimestamp(2, il.getC_Invoice().getDateInvoiced());
					}else{
						psMPO.setTimestamp(2, inl.getM_Inventory().getMovementDate());
					}
					//add ad_org_id to where clause
					if(p_AD_Org_ID>0)
						psMPO.setInt(3, p_AD_Org_ID);
	
					rsMPO = psMPO.executeQuery();
					BigDecimal qtyinvoiced = SOQty;
					while(rsMPO.next())
					{
						if(qtyinvoiced.compareTo(BigDecimal.ZERO) > 0)
						{
							FTUMatchPO[] matchPO = FTUMatchPO.get(getCtx(),  rsMPO.getInt("M_InOutLine_ID"), get_TrxName());
							for (FTUMatchPO mMatchPO : matchPO) {
								MDocType doct = ((MDocType) mMatchPO.getC_OrderLine().getC_Order().getC_DocTypeTarget());
								if(doct.get_ValueAsBoolean("IsConsignmentDocument")) {
									mMatchPO.setM_InOutLine_ID(0);
									mMatchPO.saveEx(get_TrxName());
								}
							}
							BigDecimal qty = Env.ZERO;
							if(rsMPO.getBigDecimal("Qty").compareTo(qtyinvoiced) <= 0)
							{
								qty = rsMPO.getBigDecimal("Qty");
								qtyinvoiced = qtyinvoiced.subtract(rsMPO.getBigDecimal("Qty"));
							}
							else
							{
								qty = qtyinvoiced;
								qtyinvoiced = BigDecimal.ZERO;
							}
							
							FTUMatchPO mpo = new FTUMatchPO(getCtx(), 0, get_TrxName());
							mpo.setAD_Org_ID(p_AD_Org_ID);
							mpo.setC_OrderLine_ID (orderLine.get_ID());
							mpo.setDateTrx (new Timestamp(System.currentTimeMillis()));
							mpo.setM_Product_ID (orderLine.getM_Product_ID());
							mpo.setM_InOutLine_ID(rsMPO.getInt("M_InOutLine_ID"));
							mpo.setQty(qty);
							mpo.saveEx(get_TrxName());
							//	Update QtyInvoiced on MovementLine
							MMovementLine ml = new MMovementLine(getCtx(), rsMPO.getInt("M_MovementLine_ID"), get_TrxName());
							ml.set_ValueOfColumn("QtyInvoiced", ((BigDecimal) ml.get_Value("QtyInvoiced")).add(qty) );
							ml.saveEx(get_TrxName());
						}
					}
				}
				catch(Exception e)
				{
					throw new AdempiereException("SalesReport - Create OC" + e);
				} finally {
					DB.close(rsMPO, psMPO);
					rsMPO = null;
					psMPO = null;
				}

			}
			if(order != null) {
				/*order.setDocAction(DocAction.ACTION_Prepare);
				if (!order.processIt(DocAction.ACTION_Prepare))
					throw new AdempiereException("Failed when processing document - " + order.getProcessMsg());
				else {
					order.setDocAction(DocAction.ACTION_Complete);
					if (!order.processIt(DocAction.ACTION_Complete))
						throw new AdempiereException("Failed when processing document - " + order.getProcessMsg());
						
				}*/
				order.saveEx(get_TrxName());
				noOrders++;
				info.append(" - ").append(order.getDocumentNo());
				m_info = new StringBuffer("#").append(noOrders).append(info);
				addBufferLog(order.getC_Order_ID(), order.getDateOrdered(), null,
						Msg.parseTranslation(getCtx(), "@OrderCreated@"+ order.getDocumentNo()),
						MOrder.Table_ID, order.getC_Order_ID());
				
				System.out.println("Orden "+order.getDocumentNo()+" Status "+order.getDocStatus());
			}else{
				addBufferLog(0, null, null,
						Msg.parseTranslation(getCtx(), "@Related.Sales@"),
						MOrder.Table_ID, 0);
			}
				
		} catch (Exception e) {
			rollback();
			throw new AdempiereException("SalesReport - Create OC" + e);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		return null;
	} // create OC

	public int getLocation(int C_BPartner_ID, String Type) {

		String whereClause = " IsActive = 'Y' " + " AND C_BPartner_ID =  "
				+ C_BPartner_ID + " AND " + Type + " = 'Y' ";
		MBPartnerLocation bpartnerLocation = new Query(getCtx(), 
				I_C_BPartner_Location.Table_Name, whereClause, null)
		 		.first();
		if (bpartnerLocation.get_ID() > 0)
			return bpartnerLocation.get_ID();
		return 0;
	}

} // end
