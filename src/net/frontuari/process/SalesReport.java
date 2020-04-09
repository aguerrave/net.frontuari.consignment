package net.frontuari.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.logging.Level;

import net.frontuari.base.FTUProcess;
import net.frontuari.model.FTUMatchPO;
import net.frontuari.model.MFTUMatchPOConsignment;
import net.frontuari.model.X_FTU_MatchPOConsignment;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MDocType;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * Sales Report
 *
 * @author Fernanda Carrillo
 * @author Jorge Colmenarez, Frontuari, C.A., <jlct.master@gmail.com>
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
	/** Document Type */
	private int p_C_DocType_ID = 0;
	/** Type to related invoice or internal use inventory */
	private String p_Type = null;
	/** Return Info */
	private StringBuffer	m_info = new StringBuffer();
	/** info string*/
	private StringBuilder info = new StringBuilder();
	/** Product */
	private int p_M_Product_ID = 0;

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
			}else if (name.equals("C_DocType_ID"))
				p_C_DocType_ID = para[i].getParameterAsInt();
			else if (name.equals("Type"))
				p_Type = para[i].getParameterAsString();
			else if (name.equals("M_Product_ID"))
				p_M_Product_ID = para[i].getParameterAsInt();
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
		//
		insertRecords();
		createO();
		return m_info.toString();
	} // doIt

	/**
	 * 	
	 */
	private void insertRecords() {
		// get invoiced by date range
		StringBuilder sqlInvoice = new StringBuilder("SELECT i.*,m.MovementQty "
				+ "FROM FTU_RV_Invoices i "
				+ "JOIN (SELECT C_BPartner_ID,AD_Org_ID,M_Product_ID,SUM(Qty) as MovementQty "
				+ "FROM FTU_RV_ConsignmentMovement WHERE MovementDate <= ") 
				.append(DB.TO_DATE(p_DateInvoiced2, true))
				.append(" GROUP BY C_BPartner_ID,AD_Org_ID,M_Product_ID) m ON m.M_Product_ID = i.M_Product_ID AND m.AD_Org_ID = i.AD_Org_ID AND m.MovementQty > 0 ");
				
		if(p_C_BPartner_ID >0)
			sqlInvoice.append(" AND m.C_BPartner_ID = ").append(p_C_BPartner_ID);
				
		sqlInvoice.append(" WHERE i.DateInvoiced BETWEEN  ")
				.append(DB.TO_DATE(p_DateInvoiced1, true)).append(" AND ")
				.append(DB.TO_DATE(p_DateInvoiced2, true));
		if(p_AD_Org_ID > 0)
			sqlInvoice.append(" AND i.AD_Org_ID = ").append(p_AD_Org_ID);
		
		if(p_M_Product_ID > 0)
			sqlInvoice.append(" AND i.M_Product_ID = ").append(p_M_Product_ID);
		
		sqlInvoice.append("  ORDER BY i.M_Product_ID,i.DateInvoiced ");
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
						
						MBPartner bp = new MBPartner(getCtx(), p_C_BPartner_ID, get_TrxName());
						BigDecimal priceStd = DB.getSQLValueBD(get_TrxName(), 
								"SELECT PriceStd FROM M_ProductPrice pp JOIN M_PriceList_Version plv ON pp.M_PriceList_Version_ID = plv.M_PriceList_Version_ID WHERE plv.M_PriceList_ID = ? AND pp.M_Product_ID = ? AND plv.ValidFrom <= ? ORDER BY plv.ValidFrom DESC", 
								new Object[]{bp.getPO_PriceList_ID(),rs1.getInt("M_Product_ID"), rs1.getTimestamp("DateInvoiced")});
						
						String clauseWhere = " AD_Org_ID = ? AND C_BPartner_ID = ? AND M_Product_ID = ? AND PriceStd = ? ";
						if(rs1.getInt("C_InvoiceLine_ID")>0)
							clauseWhere += " AND C_InvoiceLine_ID = "+rs1.getInt("C_InvoiceLine_ID");
						if(rs1.getInt("M_InventoryLine_ID")>0)
							clauseWhere += " AND M_InventoryLine_ID = "+rs1.getInt("M_InventoryLine_ID");
						
						MFTUMatchPOConsignment mpoc = (MFTUMatchPOConsignment) new Query(getCtx(), X_FTU_MatchPOConsignment.Table_Name, clauseWhere, get_TrxName())
								.setParameters(new Object[]{(p_AD_Org_ID > 0 ? p_AD_Org_ID: rs1.getInt("AD_Org_ID")),
										p_C_BPartner_ID,rs1.getInt("M_Product_ID"),priceStd})
								.first();
						
						if(mpoc == null)
							mpoc = new MFTUMatchPOConsignment(getCtx(), 0, get_TrxName());
						
						mpoc.setAD_Org_ID((p_AD_Org_ID > 0 ? p_AD_Org_ID: rs1.getInt("AD_Org_ID")));
						mpoc.setSOQty(SOQty);
						mpoc.setDateInvoiced(rs1.getTimestamp("DateInvoiced"));
						mpoc.setType(rs1.getString("Type"));
						mpoc.setPriceStd(priceStd);
						mpoc.setM_Warehouse_ID(p_M_Warehouse_ID);
						mpoc.setC_DocType_ID(p_C_DocType_ID);
						if(p_C_BPartner_ID>0)
							mpoc.setC_BPartner_ID(p_C_BPartner_ID);
						
						mpoc.setC_BPartner_BPartner_Parent_ID(rs1.getInt("C_BPartner_ID"));
						mpoc.setPriceActual(rs1.getBigDecimal("PriceActual"));
						mpoc.setQtyInvoiced(rs1.getBigDecimal("QtyInvoiced"));
						mpoc.setM_Product_ID(rs1.getInt("M_Product_ID"));
						if(rs1.getInt("C_InvoiceLine_ID")>0)
						{
							mpoc.setC_Invoice_ID(rs1.getInt("C_Invoice_ID"));
							mpoc.setC_InvoiceLine_ID(rs1.getInt("C_InvoiceLine_ID"));
						}
						if(rs1.getInt("M_InventoryLine_ID")>0)
						{
							mpoc.setM_Inventory_ID(rs1.getInt("M_Inventory_ID"));
							mpoc.setM_InventoryLine_ID(rs1.getInt("M_InventoryLine_ID"));
						}
						
						mpoc.saveEx(get_TrxName());						
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
		StringBuilder sql = new StringBuilder( "SELECT AD_Org_ID,M_Product_ID,PriceStd,SUM(SOQty) AS SOQty "
				+ " FROM FTU_MatchPOConsignment "
				+ " WHERE C_BPartner_ID = ? "
					+ " AND DateInvoiced BETWEEN ?  AND ? ");
		if (p_Type != null)
			sql.append(" AND Type = ? ");
		
		if(p_M_Product_ID > 0)
			sql.append(" AND M_Product_ID = ").append(p_M_Product_ID);
		
		//	Group by Product
		sql.append(" GROUP BY AD_Org_ID,M_Product_ID,PriceStd ");
		//	Order by Product
		sql.append(" ORDER BY AD_Org_ID,M_Product_ID,PriceStd ");
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		MOrder order = null;
		MOrderLine orderLine = null;
		int line = 10;
		try {
			pstmt = DB.prepareStatement(sql.toString(), get_TrxName());			
			pstmt.setInt(1, p_C_BPartner_ID);
			pstmt.setTimestamp(2, p_DateInvoiced1);
			pstmt.setTimestamp(3, p_DateInvoiced2);
			if(p_Type != null)
				pstmt.setString(4, p_Type);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				MBPartner bp = new MBPartner(getCtx(), p_C_BPartner_ID, get_TrxName());
				if (order == null) {
					log.log(Level.WARNING, "", "Creando Orden ");
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
					order.setM_Warehouse_ID(p_M_Warehouse_ID);
					order.setC_Currency_ID(bp.getPO_PriceList().getC_Currency_ID());
					order.setSalesRep_ID(bp.getSalesRep_ID());
					order.setPaymentRule(MOrder.PAYMENTRULE_OnCredit);
					order.setM_PriceList_ID(bp.getPO_PriceList_ID());
					order.setIsSOTrx(false);
					order.setC_PaymentTerm_ID(1000016); // Buscar Terminos de Pago que no sean Manuales y que no tengan plan de amortizacion, personalizacion biomercados.
					order.saveEx(get_TrxName());
					log.log(Level.WARNING, "", "Orden: "+order.getDocumentInfo());
				}
				
				BigDecimal SOQty = rs.getBigDecimal("SOQty");
				// create lines
				log.log(Level.WARNING, "", "Creando Linea de Orden ");
				orderLine = new MOrderLine(order);
				orderLine.setLine(line);
				orderLine.setM_Product_ID(rs.getInt("M_Product_ID"), true);
				orderLine.setQtyEntered(SOQty);
				orderLine.setQtyOrdered(SOQty);
				orderLine.setPrice(rs.getBigDecimal("PriceStd"));
				orderLine.saveEx(get_TrxName());
				log.log(Level.WARNING, "", "Linea de Orden: "+orderLine.get_ID());
				// add reference to final order
				String sqlUp = " UPDATE FTU_MatchPOConsignment "
						+ " SET C_OrderLine_ID = "+orderLine.get_ID()
						+ " WHERE C_OrderLine_ID IS NULL "
						+ " AND M_Product_ID = "+orderLine.getM_Product_ID()
						+ " AND AD_Org_ID = "+orderLine.getAD_Org_ID()
						+ " AND PriceStd = "+orderLine.getPriceActual();
				
				log.log(Level.WARNING, "", "Actualizando MatchPOConsingment "+sqlUp);
				DB.executeUpdate(sqlUp, get_TrxName());

				line += 10;
				String orgclause ="";
				if(p_AD_Org_ID>0)
					orgclause = " AND AD_Org_ID = ? ";
				String sqlMPO = "SELECT * FROM FTU_RV_ConsignmentMovement WHERE M_Product_ID = ? AND MovementDate <= ? "+orgclause+" AND Qty <> 0 ORDER BY Created ASC";
				PreparedStatement psMPO = null;
				ResultSet rsMPO = null;
				try{
					psMPO = DB.prepareStatement(sqlMPO, get_TrxName());
					psMPO.setInt(1, rs.getInt("M_Product_ID"));
					psMPO.setTimestamp(2, p_DateInvoiced2);
					//add ad_org_id to where clause
					if(p_AD_Org_ID>0)
						psMPO.setInt(3, p_AD_Org_ID);
	
					rsMPO = psMPO.executeQuery();
					BigDecimal qtyinvoiced = SOQty;
					while(rsMPO.next())
					{
						log.log(Level.WARNING, "", "Consultando MatchPO");
						if(qtyinvoiced.compareTo(BigDecimal.ZERO) > 0)
						{
							log.log(Level.WARNING, "", "Actualizando MatchPO");
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
							
							log.log(Level.WARNING, "", "Creando MatchPO");
							
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
			//	Check if have product when different prices
			MOrder norder = findProductsWithDifferentPrice(order);
			MOrder norder1 = null;
			//	Search if have other duplicate product
			if(norder != null)
			{
				norder1 = findProductsWithDifferentPrice(norder);
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
				if(norder != null)
					addBufferLog(norder.getC_Order_ID(), norder.getDateOrdered(), null,
							Msg.parseTranslation(getCtx(), "@OrderCreated@"+ norder.getDocumentNo()),
							MOrder.Table_ID, norder.getC_Order_ID());
				if(norder1 != null)
					addBufferLog(norder1.getC_Order_ID(), norder1.getDateOrdered(), null,
							Msg.parseTranslation(getCtx(), "@OrderCreated@"+ norder1.getDocumentNo()),
							MOrder.Table_ID, norder1.getC_Order_ID());
				
				log.log(Level.WARNING, "", "Orden "+order.getDocumentNo()+" Status "+order.getDocStatus());
			}else{
				addBufferLog(0, null, null,
						Msg.parseTranslation(getCtx(), "@Related.Sales@"),
						MOrder.Table_ID, 0);
			}
				
		} catch (Exception e) {
			rollback();
			throw new AdempiereException("SalesReport - Create OC " + e);
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
	
	private MOrder findProductsWithDifferentPrice(MOrder o)
	{
		String sql = "SELECT M_Product_ID,PriceActual FROM C_OrderLine WHERE C_Order_ID = ? "
				+ "GROUP BY M_Product_ID,PriceActual "
				+ "ORDER BY M_Product_ID,PriceActual";
		PreparedStatement pstmt = null;
		ResultSet rs = null;

		MOrder nord = null;
		try {
			pstmt = DB.prepareStatement(sql.toString(), get_TrxName());
			pstmt.setInt(1, o.get_ID());
			rs = pstmt.executeQuery();
			int ProductID = 0;
			BigDecimal Price = BigDecimal.ZERO;
			while (rs.next()) {
				//	First iterator
				if(ProductID == 0)
				{
					ProductID = rs.getInt("M_Product_ID");
					Price = rs.getBigDecimal("PriceActual");
				}
				else if(ProductID != 0 && (ProductID == rs.getInt("M_Product_ID") && Price.compareTo(rs.getBigDecimal("PriceActual")) != 0))
				{
					ProductID = rs.getInt("M_Product_ID");
					Price = rs.getBigDecimal("PriceActual");
					//	Create new Order 
					if(nord==null)
					{
						log.log(Level.WARNING, "", "Creando Orden");
						nord = new MOrder(getCtx(), 0, get_TrxName());
						PO.copyValues(o, nord);
						nord.setDocumentNo(null);
						nord.setAD_Org_ID(o.getAD_Org_ID());
						nord.saveEx(get_TrxName());
					}
					//	End Create New Order
					//	Create new Lines and Disable old Lines
					createnewLine(nord,o.get_ID(),ProductID,Price);
				}
				//	Last Iterator
				else if(ProductID != 0 && ProductID != rs.getInt("M_Product_ID"))
				{
					ProductID = rs.getInt("M_Product_ID");
					Price = rs.getBigDecimal("PriceActual");
				}
			}
		} catch (Exception e) {
			rollback();
			throw new AdempiereException("Create Other OC " + e);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if(nord!=null)
			m_info = m_info.append(nord.getDocumentNo());
		return nord;
	}
	
	private void createnewLine(MOrder o, int oID, int ProductID, BigDecimal Price)
	{
		//	Get Lines
		StringBuilder whereClauseFinal = new StringBuilder(" C_Order_ID=? AND M_Product_ID=? AND PriceActual=? ");
		String orderClause = MOrderLine.COLUMNNAME_Line;
		List<MOrderLine> list = new Query(getCtx(), I_C_OrderLine.Table_Name, whereClauseFinal.toString(), get_TrxName())
			.setParameters(new Object[]{oID,ProductID,Price})
			.setOrderBy(orderClause)
			.list();
		MOrderLine[] ol = list.toArray(new MOrderLine[list.size()]);
		for(MOrderLine line : ol)
		{
			//	Create new Line
			log.log(Level.WARNING, "", "Creando Linea Orden");
			MOrderLine nline = new MOrderLine(o);
			PO.copyValues(line, nline);
			nline.setAD_Org_ID(line.getAD_Org_ID());
			nline.setQty(line.getQtyEntered());
			nline.setPrice(line.getPriceActual());
			nline.setC_Order_ID(o.get_ID());
			
			nline.saveEx(get_TrxName());
			log.log(Level.WARNING, "", "Linea Orden: "+nline.get_ID());
			//	Transfer MatchPO
			log.log(Level.WARNING, "", "Actualizando MatchPO: "+"UPDATE M_MatchPO SET C_OrderLine_ID ="+nline.get_ID()+" WHERE C_OrderLine_ID ="+line.get_ID());
			DB.executeUpdate("UPDATE M_MatchPO SET C_OrderLine_ID ="+nline.get_ID()+" WHERE C_OrderLine_ID ="+line.get_ID(),get_TrxName());
			//	Transfer MatchPOConsignment
			log.log(Level.WARNING, "", "Actualizando FTU_MatchPOConsignment: "+"UPDATE FTU_MatchPOConsignment SET C_OrderLine_ID ="+nline.get_ID()+" WHERE C_OrderLine_ID ="+line.get_ID());
			DB.executeUpdate("UPDATE FTU_MatchPOConsignment SET C_OrderLine_ID ="+nline.get_ID()+" WHERE C_OrderLine_ID ="+line.get_ID(),get_TrxName());
			//	Disable Old Line
			log.log(Level.WARNING, "", "Actualizando Linea Orden vieja: "+line.get_ID());
			line.addDescription("Transferido a nueva orden: "+o.getDocumentNo());
			line.setQty(BigDecimal.ZERO);
			line.setPrice(BigDecimal.ZERO);
			line.setIsActive(false);
			line.saveEx(get_TrxName());
		}		
	}	//	createnewLine

} // end
